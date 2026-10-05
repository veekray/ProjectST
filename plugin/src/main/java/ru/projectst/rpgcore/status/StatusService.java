package ru.projectst.rpgcore.status;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import ru.projectst.rpgcore.damage.DefenderState;

/**
 * Активные статусы и разрешение конфликтов между ними.
 *
 * <p>Это тот модуль, ради которого затевался проект. Вся «борьба с наложением
 * механик» сводится к одному: правила объявлены в {@link StatusDef}, решение
 * принимается в одном месте, и у каждого наложения есть явный исход с именем
 * сработавшего правила.
 *
 * <h2>Порядок проверок при наложении</h2>
 * <ol>
 *   <li>статус должен быть объявлен — иначе исключение, это ошибка кода;</li>
 *   <li>блокировка: активный статус, у которого этот в {@code blocks};</li>
 *   <li>повторное наложение уже активного — по правилу {@link Stacking};</li>
 *   <li>исключительность категории: сравнение приоритетов;</li>
 *   <li>{@code removes}: наложение снимает перечисленные;</li>
 *   <li>вставка и проверка подавления.</li>
 * </ol>
 *
 * <p>Время задаётся поставщиком тика, а не берётся из Bukkit: иначе этот класс
 * нельзя было бы проверить без запуска сервера.
 */
public final class StatusService {

    private final StatusRegistry registry;
    private final LongSupplier clock;
    private final Map<UUID, Map<String, ActiveStatus>> byTarget = new HashMap<>();

    public StatusService(StatusRegistry registry, LongSupplier clock) {
        this.registry = registry;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ наложение

    public StatusOutcome apply(UUID target, StatusApplication application) {
        StatusDef def = registry.require(application.statusId());
        long now = clock.getAsLong();
        Map<String, ActiveStatus> active = holder(target);
        expire(active, now);

        int duration = application.duration() > 0 ? application.duration() : def.duration();
        long expiry = now + duration;

        // 2. блокировка
        Optional<ActiveStatus> blocker = acting(active).stream()
                .filter(s -> s.def().blocks().contains(def.id()))
                .findFirst();
        if (blocker.isPresent()) {
            return StatusOutcome.of(StatusOutcome.Kind.BLOCKED, blocker.get().id(),
                    "blocks");
        }

        // 3. повторное наложение
        ActiveStatus existing = active.get(def.id());
        if (existing != null) {
            return reapply(existing, def, application, expiry, duration, active);
        }

        // 4. исключительность категории
        StatusOutcome.Kind kind = StatusOutcome.Kind.APPLIED;
        String replaced = null;
        if (def.exclusiveWith() != null) {
            List<ActiveStatus> rivals = acting(active).stream()
                    .filter(s -> s.category() == def.exclusiveWith())
                    .sorted(Comparator.comparingInt((ActiveStatus s) -> s.def().priority()).reversed())
                    .toList();
            if (!rivals.isEmpty()) {
                ActiveStatus strongest = rivals.get(0);
                if (strongest.def().priority() > def.priority()) {
                    return StatusOutcome.of(StatusOutcome.Kind.BLOCKED, strongest.id(),
                            "exclusive: приоритет активного выше");
                }
                for (ActiveStatus rival : rivals) {
                    active.remove(rival.id());
                }
                kind = StatusOutcome.Kind.REPLACED;
                replaced = strongest.id();
            }
        }

        // 5. removes
        for (String victim : def.removes()) {
            active.remove(victim);
        }

        // 6. вставка и подавление
        active.put(def.id(), new ActiveStatus(def, application.source(), 1, expiry,
                application.amount()));
        Optional<String> suppressor = suppressedBy(active, def.id());
        if (suppressor.isPresent()) {
            return StatusOutcome.of(StatusOutcome.Kind.APPLIED_SUPPRESSED, suppressor.get(),
                    "suppresses");
        }
        return StatusOutcome.of(kind, replaced,
                kind == StatusOutcome.Kind.REPLACED ? "exclusive" : "applied");
    }

    private StatusOutcome reapply(ActiveStatus existing, StatusDef def,
                                  StatusApplication application, long expiry, int duration,
                                  Map<String, ActiveStatus> active) {
        // Щиты складываются, остальные величины заменяются.
        //
        // Складываются потому, что два щита подряд — это два щита: заменять
        // означало бы, что второй каст иногда ослабляет защиту, и объяснить это
        // игроку нечем. Для прочих статусов замена верна: щит... то есть эффект
        // на 40 не должен остаться эффектом на 10 только из-за того, что
        // предыдущий ещё не истёк.
        if (application.amount() > 0) {
            if (def.category() == StatusCategory.SHIELD) {
                existing.setAmount(existing.amount() + application.amount());
            } else {
                existing.setAmount(application.amount());
            }
        }
        StatusOutcome.Kind kind = switch (def.stacking()) {
            case NONE -> StatusOutcome.Kind.IGNORED;
            case REFRESH -> {
                existing.refresh(expiry);
                yield StatusOutcome.Kind.REFRESHED;
            }
            case EXTEND -> {
                existing.extend(duration);
                yield StatusOutcome.Kind.EXTENDED;
            }
            case STACKS -> {
                if (existing.stacks() >= def.maxStacks()) {
                    existing.refresh(expiry);
                    yield StatusOutcome.Kind.IGNORED;
                }
                existing.addStack(expiry);
                yield StatusOutcome.Kind.STACKED;
            }
        };
        String rule = kind == StatusOutcome.Kind.IGNORED && def.stacking() == Stacking.STACKS
                ? "stacking: достигнут максимум стаков"
                : "stacking: " + def.stacking().name().toLowerCase(java.util.Locale.ROOT);
        Optional<String> suppressor = suppressedBy(active, def.id());
        if (suppressor.isPresent() && kind != StatusOutcome.Kind.IGNORED) {
            return StatusOutcome.of(StatusOutcome.Kind.APPLIED_SUPPRESSED, suppressor.get(),
                    "suppresses");
        }
        return StatusOutcome.of(kind, rule);
    }

    // ------------------------------------------------------------------ снятие

    public boolean remove(UUID target, String statusId) {
        Map<String, ActiveStatus> active = byTarget.get(target);
        return active != null && active.remove(statusId) != null;
    }

    /** Снимает все статусы, наложенные этим источником. */
    /**
     * Снимает один стак статуса; на последнем снимает статус целиком.
     *
     * <p>Нужно там, где стаки — это заряды: кора друида держит один удар или
     * два, и каждый ответ тратит ровно один. Снимать статус целиком было бы
     * неверно, а не снимать ничего — тем самым «эффект, который не кончается».
     *
     * @return {@code true}, если что-то сняли
     */
    public boolean removeStack(UUID target, String statusId) {
        for (ActiveStatus status : all(target)) {
            if (status.id().equals(statusId)) {
                if (status.removeStack()) {
                    remove(target, statusId);
                }
                return true;
            }
        }
        return false;
    }

    public int removeSource(UUID target, String source) {
        Map<String, ActiveStatus> active = byTarget.get(target);
        if (active == null) {
            return 0;
        }
        List<String> doomed = active.values().stream()
                .filter(s -> s.source().equals(source))
                .map(ActiveStatus::id)
                .toList();
        doomed.forEach(active::remove);
        return doomed.size();
    }

    public void forget(UUID target) {
        byTarget.remove(target);
    }

    /** Снимает истёкшие у всех целей. Зовётся планировщиком. */
    public void expireAll() {
        long now = clock.getAsLong();
        byTarget.values().forEach(active -> expire(active, now));
    }

    // ------------------------------------------------------------------ запросы

    /** Все наложенные статусы, включая подавленные. */
    public List<ActiveStatus> all(UUID target) {
        Map<String, ActiveStatus> active = byTarget.get(target);
        if (active == null) {
            return List.of();
        }
        expire(active, clock.getAsLong());
        return List.copyOf(active.values());
    }

    /** Только действующие: подавленные исключены. */
    /**
     * Во сколько раз ослаблен удар вызванного по тому, кто его не вызывал.
     *
     * <p>Ядро рыцаря. Вызванный бьёт вызвавшего в полную силу, а всех
     * остальных — вполсилы: выбор переходит к противнику, и оба варианта
     * чего-то стоят. Обойти нельзя, можно только снять вызов.
     *
     * <p>Величина берётся из самого статуса, а не из кода: сколько именно
     * отнимает вызов, решает содержимое навыка, как и всё прочее в этом ядре.
     * Берётся сильнейший из действующих вызовов — иначе второй, более слабый,
     * ослаблял бы первый.
     *
     * @return множитель от нуля до единицы; единица — ослаблять нечем
     */
    public double challengeScale(UUID attacker, UUID victim) {
        if (attacker == null || victim == null) {
            return 1;
        }
        double worst = 1;
        for (ActiveStatus status : acting(attacker)) {
            if (!status.def().tags().contains("challenge")) {
                continue;
            }
            // Источник хранит имя вызвавшего: удар по нему самому не ослабляется.
            String source = status.source();
            if (source != null && source.endsWith(":" + victim)) {
                continue;
            }
            worst = Math.min(worst, Math.max(0, 1 - status.amount() / 100.0));
        }
        return worst;
    }

    public List<ActiveStatus> acting(UUID target) {
        Map<String, ActiveStatus> active = byTarget.get(target);
        if (active == null) {
            return List.of();
        }
        expire(active, clock.getAsLong());
        return acting(active);
    }

    /** Статус наложен и действует. */
    public boolean isActing(UUID target, String statusId) {
        return acting(target).stream().anyMatch(s -> s.id().equals(statusId));
    }

    /** Статус наложен, но может быть подавлен. */
    public boolean isPresent(UUID target, String statusId) {
        return all(target).stream().anyMatch(s -> s.id().equals(statusId));
    }

    /**
     * Почему статус не действует. Это и есть ответ команды {@code /rpg why}:
     * пустой результат означает, что статус действует или вовсе не наложен.
     */
    public Optional<String> suppressedBy(UUID target, String statusId) {
        Map<String, ActiveStatus> active = byTarget.get(target);
        if (active == null) {
            return Optional.empty();
        }
        expire(active, clock.getAsLong());
        return suppressedBy(active, statusId);
    }

    // ------------------------------------------------------------------ связь с уроном

    /**
     * Состояние цели для конвейера урона: неуязвимость и запас щитов.
     * Подавленные статусы не учитываются — подавленный щит не поглощает.
     */
    public DefenderState defenderState(UUID target) {
        List<ActiveStatus> acting = acting(target);
        boolean immune = acting.stream().anyMatch(s -> s.category() == StatusCategory.IMMUNITY);
        double pool = acting.stream()
                .filter(s -> s.category() == StatusCategory.SHIELD)
                .mapToDouble(ActiveStatus::amount)
                .sum();
        return new DefenderState(immune, pool);
    }

    /**
     * Списывает поглощённый урон со щитов. Расходуется тот, что истечёт
     * раньше: иначе короткий щит сгорит по таймеру, не сделав работы.
     *
     * @return сколько фактически удалось списать
     */
    public double consumeShield(UUID target, double amount) {
        if (amount <= 0) {
            return 0;
        }
        Map<String, ActiveStatus> active = byTarget.get(target);
        if (active == null) {
            return 0;
        }
        long now = clock.getAsLong();
        expire(active, now);

        List<ActiveStatus> shields = acting(active).stream()
                .filter(s -> s.category() == StatusCategory.SHIELD)
                .sorted(Comparator.comparingLong(ActiveStatus::expiresAtTick))
                .toList();

        double left = amount;
        for (ActiveStatus shield : shields) {
            if (left <= 0) {
                break;
            }
            double taken = Math.min(shield.amount(), left);
            shield.setAmount(shield.amount() - taken);
            left -= taken;
            if (shield.amount() <= 0) {
                active.remove(shield.id());
            }
        }
        return amount - left;
    }

    // ------------------------------------------------------------------ внутреннее

    private Map<String, ActiveStatus> holder(UUID target) {
        return byTarget.computeIfAbsent(target, k -> new LinkedHashMap<>());
    }

    private static void expire(Map<String, ActiveStatus> active, long now) {
        active.values().removeIf(s -> s.expired(now));
    }

    private static List<ActiveStatus> acting(Map<String, ActiveStatus> active) {
        List<ActiveStatus> out = new ArrayList<>(active.size());
        for (ActiveStatus candidate : active.values()) {
            if (suppressedBy(active, candidate.id()).isEmpty()) {
                out.add(candidate);
            }
        }
        return out;
    }

    private static Optional<String> suppressedBy(Map<String, ActiveStatus> active, String statusId) {
        return suppressedBy(active, statusId, new HashSet<>());
    }

    /**
     * Подавленный статус сам не подавляет: иначе цепочка «A подавляет B, B
     * подавляет C» освобождала бы C неверно. Граф подавления — DAG, циклы
     * отвергаются при загрузке, но на случай реестра, собранного в коде, стоит
     * защита от бесконечной рекурсии: при цикле считаем статус действующим.
     */
    private static Optional<String> suppressedBy(Map<String, ActiveStatus> active,
                                                 String statusId, Set<String> visiting) {
        if (!visiting.add(statusId)) {
            return Optional.empty();
        }
        try {
            for (ActiveStatus other : active.values()) {
                if (other.id().equals(statusId)) {
                    continue;
                }
                if (!other.def().suppresses().contains(statusId)) {
                    continue;
                }
                if (suppressedBy(active, other.id(), visiting).isEmpty()) {
                    return Optional.of(other.id());
                }
            }
            return Optional.empty();
        } finally {
            visiting.remove(statusId);
        }
    }

    /** Для отладочной команды: сколько целей вообще под статусами. */
    /**
     * Кого вообще касались статусы.
     *
     * <p>Нужно сверке с миром: обездвиженных надо находить, не обходя все
     * сущности всех миров.
     */
    public java.util.Set<UUID> targets() {
        return java.util.Set.copyOf(byTarget.keySet());
    }

    public int trackedTargets() {
        return byTarget.size();
    }

    /** Для отладочной команды: имена источников активных статусов. */
    public Collection<String> sources(UUID target) {
        return all(target).stream().map(ActiveStatus::source).distinct().toList();
    }
}
