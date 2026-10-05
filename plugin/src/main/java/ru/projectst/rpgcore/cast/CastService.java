package ru.projectst.rpgcore.cast;

import java.util.Optional;
import java.util.UUID;
import ru.projectst.rpgcore.balance.BalanceBook;
import ru.projectst.rpgcore.classes.ClassDef;
import ru.projectst.rpgcore.classes.ClassService;
import ru.projectst.rpgcore.damage.StatIds;
import ru.projectst.rpgcore.skill.SkillDef;
import ru.projectst.rpgcore.skill.SkillRegistry;
import ru.projectst.rpgcore.skill.SkillRuntime;
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
 * запрещающие статусы → перезарядка → мана. Мана списывается последней, то
 * есть отказ по любой другой причине её не трогает.
 */
public final class CastService {

    /** Статус с этой меткой запрещает касты, пока действует. */
    public static final String TAG_BLOCKS_CAST = "blocks-cast";

    private final ClassService classes;
    private final SkillRegistry skills;
    private final BalanceBook balance;
    private final StatusService statuses;
    private final StatusRegistry statusDefs;
    private final StatService stats;
    private final ManaPool mana;
    private final CooldownTracker cooldowns;
    private final SkillRuntime runtime;

    public CastService(ClassService classes, SkillRegistry skills, BalanceBook balance,
                       StatusService statuses, StatusRegistry statusDefs, StatService stats,
                       ManaPool mana, CooldownTracker cooldowns, SkillRuntime runtime) {
        this.classes = classes;
        this.skills = skills;
        this.balance = balance;
        this.statuses = statuses;
        this.statusDefs = statusDefs;
        this.stats = stats;
        this.mana = mana;
        this.cooldowns = cooldowns;
        this.runtime = runtime;
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

    /** Применить навык по идентификатору. */
    public CastOutcome cast(UUID player, String skillId) {
        Optional<SkillDef> found = skills.find(skillId);
        if (found.isEmpty()) {
            return CastOutcome.of(CastOutcome.Kind.UNKNOWN_SKILL, skillId);
        }
        SkillDef skill = found.get();

        Optional<ClassDef> def = classes.classOf(player);
        if (def.isEmpty()) {
            return CastOutcome.of(CastOutcome.Kind.NO_CLASS);
        }
        if (!skill.classId().equals(def.get().id())) {
            return CastOutcome.of(CastOutcome.Kind.WRONG_CLASS,
                    "навык принадлежит классу " + skill.classId());
        }
        int level = classes.skillLevel(player, skillId);
        if (level == 0) {
            return CastOutcome.of(CastOutcome.Kind.NOT_UNLOCKED, skillId);
        }

        Optional<ActiveStatus> blocker = blockingStatus(player);
        if (blocker.isPresent()) {
            return CastOutcome.of(CastOutcome.Kind.BLOCKED,
                    "мешает " + blocker.get().id());
        }

        long left = cooldowns.remaining(player, skillId);
        if (left > 0) {
            return CastOutcome.of(CastOutcome.Kind.ON_COOLDOWN,
                    "осталось " + seconds(left) + " с");
        }

        double cost = skill.manaCost().resolve(balance.table(skillId), level);
        if (!mana.has(player, cost)) {
            return CastOutcome.of(CastOutcome.Kind.NOT_ENOUGH_MANA,
                    "нужно " + round(cost) + ", есть " + round(mana.current(player)));
        }

        // Всё проверено — только теперь тратим. Обратного порядка быть не
        // может: списанная мана при последующем отказе не возвращается ничем.
        mana.spend(player, cost);
        cooldowns.start(player, skillId, cooldownTicks(player, skill, level));
        runtime.cast(player, skill, level);
        return CastOutcome.cast();
    }

    /** Действующий статус, запрещающий касты, если такой есть. */
    public Optional<ActiveStatus> blockingStatus(UUID player) {
        for (ActiveStatus status : statuses.acting(player)) {
            if (statusDefs.find(status.id())
                    .filter(d -> d.hasTag(TAG_BLOCKS_CAST)).isPresent()) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }

    /**
     * Перезарядка в тиках с учётом стата сокращения.
     *
     * <p>Сокращение ограничено восемьюдесятью процентами. Без границы хватило
     * бы набора снаряжения, чтобы перезарядка ушла в ноль, и навык стрелял бы
     * каждый тик — проверять это на живом сервере слишком дорого.
     */
    public long cooldownTicks(UUID player, SkillDef skill, int level) {
        double seconds = skill.cooldown().resolve(balance.table(skill.id()), level);
        double reduction = Math.min(80, Math.max(0,
                stats.snapshot(player).get(StatIds.COOLDOWN_REDUCTION)));
        return Math.round(seconds * 20 * (1 - reduction / 100));
    }

    /** Навык в слоте: нужен, чтобы ответ игроку называл навык, а не номер. */
    public Optional<SkillDef> skillInSlot(UUID player, int slot) {
        return classes.skillInSlot(player, slot);
    }

    public ManaPool mana() {
        return mana;
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
