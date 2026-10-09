package ru.projectst.rpgcore.cast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ru.projectst.rpgcore.balance.BalanceBook;
import ru.projectst.rpgcore.classes.ClassDef;
import ru.projectst.rpgcore.classes.ClassService;
import ru.projectst.rpgcore.classes.ResourceSpec;
import ru.projectst.rpgcore.damage.StatIds;
import ru.projectst.rpgcore.skill.CastContext;
import ru.projectst.rpgcore.skill.Heading;
import ru.projectst.rpgcore.skill.SkillDef;
import ru.projectst.rpgcore.skill.SkillRegistry;
import ru.projectst.rpgcore.skill.SkillRuntime;
import ru.projectst.rpgcore.skill.SkillTrigger;
import ru.projectst.rpgcore.stat.StatService;
import ru.projectst.rpgcore.status.ActiveStatus;
import ru.projectst.rpgcore.status.StatusRegistry;
import ru.projectst.rpgcore.status.StatusService;

/**
 * Единственный вход в применение навыка.
 *
 * <p>Проверки, списание ресурсов и запуск собраны в одном месте намеренно. В
 * старом стеке их было минимум три: MMOCore считал ману, MythicLib — кулдаун,
 * а сам метаскилл мог ещё раз проверить что-нибудь своё, и порядок между ними
 * нигде не был записан. Отсюда же росло худшее: нажатие впустую без причины.
 *
 * <p>Порядок здесь зафиксирован и проверяется тестами: право на навык →
 * запрещающие статусы → перезарядка → запасы. Запасы списываются последними, то
 * есть отказ по любой другой причине их не трогает. Запасов двое — ресурс класса
 * и выносливость, — и проверяются оба <b>до</b> списания любого: иначе навык,
 * которому хватило маны и не хватило выносливости, забирал бы ману ни за что.
 */
public final class CastService {

    /** Статус с этой меткой запрещает касты, пока действует. */
    public static final String TAG_BLOCKS_CAST =
            ru.projectst.rpgcore.status.StatusTags.BLOCKS_CAST;

    /** Статус с этой меткой запрещает навыки, которые двигают кастера. */
    public static final String TAG_IMMOBILIZE =
            ru.projectst.rpgcore.status.StatusTags.IMMOBILIZE;

    private final ClassService classes;
    private final SkillRegistry skills;
    private final BalanceBook balance;
    private final StatusService statuses;
    private final StatusRegistry statusDefs;
    private final StatService stats;
    private final ResourcePool resource;
    private final CooldownTracker cooldowns;
    private final SkillRuntime runtime;

    /**
     * Кто сейчас готовит навык.
     *
     * <p>Подготовка — решение владельца: навык с {@code cast-time} не бьёт по
     * нажатию, кастер сначала его готовит, и это видно всем. Запасы и
     * перезарядка списываются в начале — сорванный каст пропадает, это его цена.
     * Срывает подготовку любой статус, запрещающий касты: оглушение, тишина,
     * изъятие. Движение не срывает, но пока идёт подготовка, кастер ходит
     * ровно вполовину базовой скорости — это держит платформа.
     */
    private final Map<UUID, Casting> casting = new java.util.concurrent.ConcurrentHashMap<>();

    /** Тики службы: по ним считается конец подготовки. */
    private long now;

    private CastListener listener = CastListener.NONE;

    /** Подготовка навыка: что, с какими числами и до какого тика. */
    private record Casting(SkillDef skill, CastContext context, long endsAt, int total) {
    }

    /** Кому сказать о начале и конце подготовки: платформе — моду и скорости. */
    public interface CastListener {
        CastListener NONE = new CastListener() {
            @Override
            public void started(UUID player, SkillDef skill, int ticks) {
            }

            @Override
            public void ended(UUID player, SkillDef skill, boolean completed, String reason) {
            }
        };

        void started(UUID player, SkillDef skill, int ticks);

        /**
         * @param completed навык сработал; {@code false} — сорван
         * @param reason    почему сорван, словами для игрока; при успехе пусто
         */
        void ended(UUID player, SkillDef skill, boolean completed, String reason);
    }

    /** Игроки, чьи пассивки сейчас срабатывают: защита от повторного входа. */
    private final Set<UUID> firing = java.util.Collections.synchronizedSet(new HashSet<>());

    public CastService(ClassService classes, SkillRegistry skills, BalanceBook balance,
                       StatusService statuses, StatusRegistry statusDefs, StatService stats,
                       ResourcePool resource, CooldownTracker cooldowns, SkillRuntime runtime) {
        this.classes = classes;
        this.skills = skills;
        this.balance = balance;
        this.statuses = statuses;
        this.statusDefs = statusDefs;
        this.stats = stats;
        this.resource = resource;
        this.cooldowns = cooldowns;
        this.runtime = runtime;
    }

    public void useListener(CastListener listener) {
        this.listener = listener == null ? CastListener.NONE : listener;
    }

    /** Готовит ли игрок сейчас навык. */
    public boolean isCasting(UUID player) {
        return casting.containsKey(player);
    }

    /** Сколько тиков подготовки у навыка на этом уровне; ноль — мгновенный. */
    public int castTicks(SkillDef skill, int level) {
        return (int) Math.max(0, Math.round(
                skill.castTime().resolve(balance.table(skill.id()), level)));
    }

    /**
     * Тик подготовки: срыв статусом или срабатывание.
     *
     * <p>Зовётся платформой раз в тик сервера. Срыв проверяется раньше конца:
     * оглушение, легшее в последний тик, тоже срывает — удар, который успел бы
     * «проскочить» в тот же тик, был бы ровно тем случайным исходом, от
     * которого проект уходит.
     */
    public void tick() {
        now++;
        for (Map.Entry<UUID, Casting> entry : List.copyOf(casting.entrySet())) {
            UUID player = entry.getKey();
            Casting cast = entry.getValue();
            Optional<ActiveStatus> blocker = blockingStatus(player);
            if (blocker.isPresent()) {
                casting.remove(player);
                listener.ended(player, cast.skill(), false, "сорван: "
                        + statusDefs.find(blocker.get().id()).map(d -> d.display())
                                .filter(d -> !d.isBlank()).orElse(blocker.get().id()));
                continue;
            }
            if (now >= cast.endsAt()) {
                casting.remove(player);
                listener.ended(player, cast.skill(), true, "");
                runtime.cast(cast.context(), cast.skill(), 0);
            }
        }
    }

    /** Прервать подготовку: игрок ушёл, умер или выбрал другой навык. */
    public void interrupt(UUID player, String reason) {
        Casting cast = casting.remove(player);
        if (cast != null) {
            listener.ended(player, cast.skill(), false, reason);
        }
    }

    /** Применить навык из слота: то, что происходит по нажатию клавиши. */
    public CastOutcome castSlot(UUID player, int slot) {
        Optional<ClassDef> def = classes.classOf(player);
        if (def.isEmpty()) {
            return CastOutcome.of(CastOutcome.Kind.NO_CLASS);
        }
        if (slot < 1 || slot > def.get().slots()) {
            return CastOutcome.of(CastOutcome.Kind.BAD_SLOT,
                    "у класса слотов: " + def.get().slots());
        }
        Optional<SkillDef> skill = classes.skillInSlot(player, slot);
        if (skill.isEmpty()) {
            return CastOutcome.of(CastOutcome.Kind.EMPTY_SLOT, "слот " + slot + " пуст");
        }
        return cast(player, skill.get().id());
    }

    /**
     * Срабатывание пассивных навыков игрока по событию.
     *
     * <p>Идёт через те же ворота, что и нажатие: пассивный навык тоже стоит
     * маны, тоже уходит в перезарядку и тоже не работает под тишиной. Отдельный
     * путь означал бы второй набор правил, а именно от этого проект и уходит.
     *
     * <p>Повторный вход запрещён: навык на «я нанёс урон», который сам наносит
     * урон, иначе вызвал бы себя до переполнения стека. Защита по игроку, а не
     * по навыку — цепочка из двух навыков замкнулась бы так же.
     *
     * @param trigger что случилось
     * @param source  кто участвовал: ударивший, получивший, убитый
     * @return исходы по каждому сработавшему навыку; пусто — ни один не подошёл
     */
    public List<CastOutcome> fire(UUID player, SkillTrigger trigger, UUID source) {
        if (trigger == SkillTrigger.MANUAL) {
            throw new IllegalArgumentException("ручной триггер не срабатывает сам");
        }
        if (!firing.add(player)) {
            return List.of();
        }
        try {
            List<CastOutcome> out = new ArrayList<>();
            for (String skillId : firingCandidates(player)) {
                Optional<SkillDef> skill = skills.find(skillId);
                if (skill.isEmpty() || skill.get().trigger() != trigger) {
                    continue;
                }
                CastOutcome outcome = attempt(player, skill.get(), source);
                // Молчаливые отказы пассивок не копим: интересны только те, что
                // сработали, и те, что отказали по делу.
                if (outcome.kind() != CastOutcome.Kind.ON_COOLDOWN) {
                    out.add(outcome);
                }
            }
            return out;
        } finally {
            firing.remove(player);
        }
    }

    /**
     * Чьи навыки могут сработать: изученные плюс служебные навыки класса.
     *
     * <p>Служебные игрок не изучает — это отдачи и тики, без которых навык
     * неполон. В старом стеке они жили безымянными метаскиллами и при этом
     * попадали в меню наравне с настоящими.
     */
    private List<String> firingCandidates(UUID player) {
        List<String> out = new ArrayList<>(classes.unlockedSkills(player));
        classes.classOf(player).ifPresent(def -> {
            for (SkillDef skill : skills.all()) {
                if (skill.internal() && !skill.classId().isBlank()
                        && skill.classId().equals(def.id())) {
                    out.add(skill.id());
                }
            }
        });
        return out;
    }

    /**
     * Применить умение предмета.
     *
     * <p>Идёт через те же ворота: ресурс, перезарядка, запрещающие статусы. От
     * ручного каста отличается одним — изучения не требует, потому что право
     * даёт сам предмет. Класс при этом не проверяется, и это не послабление:
     * навык умения обязан быть служебным и без класса, что проверяет связывание.
     */
    public CastOutcome castItem(UUID player, String skillId) {
        Optional<SkillDef> found = skills.find(skillId);
        if (found.isEmpty()) {
            return CastOutcome.of(CastOutcome.Kind.UNKNOWN_SKILL, skillId);
        }
        SkillDef skill = found.get();
        if (!skill.internal()) {
            return CastOutcome.of(CastOutcome.Kind.NOT_MANUAL,
                    "навык не служебный: предмет не может его запускать");
        }
        return attempt(player, skill, null);
    }

    /**
     * Применить врождённый навык: тот, что есть у каждого игрока.
     *
     * <p>Идёт через те же ворота: запрещающие статусы, заряды, выносливость.
     * Отличий от ручного каста два, и оба следуют из «есть у всех»: изучения не
     * требует и класса не проверяет — иначе игрок до выбора класса остался бы без
     * движения.
     *
     * @param heading куда игрок идёт; {@code null} — стоит на месте, и тогда
     *                рывок пойдёт по взгляду
     */
    public CastOutcome castInnate(UUID player, Heading heading) {
        Optional<SkillDef> found = skills.innate();
        if (found.isEmpty()) {
            // Без контента врождённого навыка нет вовсе — и молчать об этом
            // нельзя: игрок нажал клавишу и обязан узнать, почему ничего.
            return CastOutcome.of(CastOutcome.Kind.UNKNOWN_SKILL,
                    "врождённого навыка нет в контенте");
        }
        return attempt(player, found.get(), null, heading);
    }

    /** Применить навык по идентификатору. */
    public CastOutcome cast(UUID player, String skillId) {
        Optional<SkillDef> found = skills.find(skillId);
        if (found.isEmpty()) {
            return CastOutcome.of(CastOutcome.Kind.UNKNOWN_SKILL, skillId);
        }
        SkillDef skill = found.get();
        if (skill.innate()) {
            // Врождённый навык по имени — это команда. Направления хода у неё
            // нет, и рывок пойдёт по взгляду; отказывать было бы хуже, чем
            // ответить «по взгляду», а молча требовать класс — тем более.
            return castInnate(player, null);
        }

        Optional<ClassDef> def = classes.classOf(player);
        if (def.isEmpty()) {
            return CastOutcome.of(CastOutcome.Kind.NO_CLASS);
        }
        if (!skill.classId().equals(def.get().id())) {
            return CastOutcome.of(CastOutcome.Kind.WRONG_CLASS,
                    "навык принадлежит классу " + skill.classId());
        }
        if (skill.passive()) {
            return CastOutcome.of(CastOutcome.Kind.NOT_MANUAL,
                    "навык срабатывает сам: "
                            + skill.trigger().name().toLowerCase(java.util.Locale.ROOT));
        }
        return attempt(player, skill, null);
    }

    /**
     * Общая часть ручного и автоматического применения.
     *
     * <p>Порядок проверок здесь один для обоих путей — это и есть смысл
     * «единственного входа». Отдельная проверка права на навык осталась у
     * вызывающих: ручному касту нужно сказать «не изучен», а срабатыванию
     * проверять нечего, оно идёт по изученным.
     */
    private CastOutcome attempt(UUID player, SkillDef skill, UUID source) {
        return attempt(player, skill, source, null);
    }

    private CastOutcome attempt(UUID player, SkillDef skill, UUID source, Heading heading) {
        String skillId = skill.id();
        int level = classes.skillLevel(player, skillId);
        if (level == 0 && (skill.internal() || skill.innate())) {
            // Служебный навык не изучают, поэтому уровень ему даёт класс.
            // Врождённый не изучают тем более: он есть сразу и у всех.
            level = 1;
        }
        if (level == 0) {
            return CastOutcome.of(CastOutcome.Kind.NOT_UNLOCKED, skillId);
        }

        Optional<ActiveStatus> blocker = blockingStatus(player);
        if (blocker.isPresent()) {
            return CastOutcome.of(CastOutcome.Kind.BLOCKED,
                    "мешает " + blocker.get().id());
        }

        // Пока идёт подготовка, второй навык с подготовкой не начать: две
        // подготовки разом — это вопрос «какая сработает», и ответ на него
        // игрок не выбирал. Мгновенный навык — можно: рывок и защитная реакция
        // как раз для того, чтобы бросить каст и спастись.
        // Нажатие — это ручной навык без источника. Периодическая пассивка тоже
        // приходит без источника (её никто не задел), но её никто не нажимал:
        // принять её за нажатие значило бы, что Опека зверя раз в две секунды
        // бросает подготовку друида.
        boolean pressed = source == null && !skill.passive();
        int castTicks = pressed ? castTicks(skill, level) : 0;
        Casting ongoing = casting.get(player);
        if (ongoing != null && pressed && castTicks > 0) {
            return CastOutcome.of(CastOutcome.Kind.BUSY,
                    "идёт подготовка: " + ongoing.skill().display());
        }

        // Обездвиженный не перемещается — ни рывком, ни телепортом, ни
        // сближением. Проверка здесь, а не в самом навыке: иначе каждый
        // перемещающий навык пришлось бы об этом помнить, а забытый означал бы
        // бесплатное снятие корней. Корни молчат про касты намеренно: они
        // запрещают ходить, а не бить.
        if (skill.movesCaster()) {
            Optional<ActiveStatus> held = immobilizingStatus(player);
            if (held.isPresent()) {
                return CastOutcome.of(CastOutcome.Kind.BLOCKED,
                        "мешает " + held.get().id() + ": перемещение под контролем");
            }
        }

        long left = cooldowns.remaining(player, skillId, skill.charges());
        if (left > 0) {
            return CastOutcome.of(CastOutcome.Kind.ON_COOLDOWN,
                    skill.charges() > 1
                            ? "зарядов нет, следующий через " + seconds(left) + " с"
                            : "осталось " + seconds(left) + " с");
        }

        double cost = skill.resourceCost().resolve(balance.table(skillId), level);
        double stamina = skill.staminaCost().resolve(balance.table(skillId), level);
        // Оба запаса проверяются до списания любого: навык, которому хватило
        // маны и не хватило выносливости, не должен забрать ману ни за что.
        if (!resource.has(player, cost)) {
            return CastOutcome.of(CastOutcome.Kind.NOT_ENOUGH_RESOURCE,
                    resource.displayName(player).toLowerCase(java.util.Locale.ROOT)
                            + ": нужно " + round(cost)
                            + ", есть " + round(resource.current(player)));
        }
        if (!resource.has(player, ResourceSpec.STAMINA, stamina)) {
            return CastOutcome.of(CastOutcome.Kind.NOT_ENOUGH_RESOURCE,
                    ResourceSpec.STAMINA.display().toLowerCase(java.util.Locale.ROOT)
                            + ": нужно " + round(stamina) + ", есть "
                            + round(resource.current(player, ResourceSpec.STAMINA)));
        }

        // Всё проверено — только теперь тратим. Обратного порядка быть не
        // может: списанный запас при последующем отказе не возвращается ничем.
        resource.spend(player, cost);
        resource.spend(player, ResourceSpec.STAMINA, stamina);
        // Периодический навык не может сработать чаще своего промежутка, даже
        // если перезарядка у него нулевая: иначе «каждые две секунды» зависело
        // бы от того, как часто его зовёт слушатель.
        cooldowns.start(player, skillId,
                Math.max(cooldownTicks(player, skill, level), skill.intervalTicks()),
                skill.charges());
        CastContext context = new CastContext(player, level, null, source,
                new java.util.HashMap<>(), heading);
        if (castTicks > 0) {
            casting.put(player, new Casting(skill, context, now + castTicks, castTicks));
            listener.started(player, skill, castTicks);
            return CastOutcome.cast();
        }
        if (pressed && ongoing != null) {
            interrupt(player, "прерван: " + skill.display());
        }
        runtime.cast(context, skill, 0);
        return CastOutcome.cast();
    }

    /** Действующий статус, запрещающий касты, если такой есть. */
    public Optional<ActiveStatus> blockingStatus(UUID player) {
        return statusWithTag(player, TAG_BLOCKS_CAST);
    }

    /** Действующий статус, запрещающий перемещение, если такой есть. */
    public Optional<ActiveStatus> immobilizingStatus(UUID player) {
        return statusWithTag(player, TAG_IMMOBILIZE);
    }

    private Optional<ActiveStatus> statusWithTag(UUID player, String tag) {
        for (ActiveStatus status : statuses.acting(player)) {
            if (statusDefs.find(status.id()).filter(d -> d.hasTag(tag)).isPresent()) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }

    /**
     * Перезарядка в тиках с учётом стата сокращения.
     *
     * <p>Сколько процентов даёт рейтинг, решает кривая стата: до ста она не
     * доходит ни при каком снаряжении, поэтому отдельный потолок больше не нужен.
     * Нижняя граница всё равно оставлена — она про другое: про неверно
     * настроенный контент, в котором кривой задали потолок больше ста. Навык,
     * стреляющий каждый тик, проверять на живом сервере слишком дорого.
     */
    public long cooldownTicks(UUID player, SkillDef skill, int level) {
        double seconds = skill.cooldown().resolve(balance.table(skill.id()), level);
        double left = Math.clamp(1 - stats.share(player, StatIds.COOLDOWN_REDUCTION), 0.1, 1);
        return Math.round(seconds * 20 * left);
    }

    /** Навык в слоте: нужен, чтобы ответ игроку называл навык, а не номер. */
    public Optional<SkillDef> skillInSlot(UUID player, int slot) {
        return classes.skillInSlot(player, slot);
    }

    /**
     * Таблица баланса навыка.
     *
     * <p>Нужна интерфейсу, чтобы показать стоимость и перезарядку <b>теми же</b>
     * числами, которыми их считает бой. Отдельный расчёт для показа — это два
     * источника правды, и однажды они разошлись бы.
     */
    public ru.projectst.rpgcore.balance.BalanceTable balanceOf(String skillId) {
        return balance.table(skillId);
    }

    public ResourcePool resource() {
        return resource;
    }

    public CooldownTracker cooldowns() {
        return cooldowns;
    }

    private static String seconds(long ticks) {
        return round(ticks / 20.0);
    }

    private static String round(double value) {
        return String.valueOf(Math.round(value * 10) / 10.0);
    }
}
