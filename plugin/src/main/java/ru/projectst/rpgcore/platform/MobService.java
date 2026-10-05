package ru.projectst.rpgcore.platform;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import ru.projectst.rpgcore.mob.MobDef;
import ru.projectst.rpgcore.mob.MobRegistry;
import ru.projectst.rpgcore.mob.MobSkill;
import ru.projectst.rpgcore.mob.MobTrigger;
import ru.projectst.rpgcore.skill.CastContext;
import ru.projectst.rpgcore.skill.SkillRegistry;
import ru.projectst.rpgcore.skill.SkillRuntime;
import ru.projectst.rpgcore.stat.StatService;

/**
 * Появление мобов и запуск их навыков.
 *
 * <p>Метка в постоянном контейнере, а не имя над головой: имя меняет первый же
 * бафф, перевод или другой плагин, и моб, узнаваемый по имени, перестаёт быть
 * собой — вместе со своими статами, навыками и дропом.
 *
 * <p>Статы моба уходят в тот же {@link StatService}, что статы игрока. Поэтому
 * «магическое сопротивление» у моба значит ровно то же, что у игрока: в прежнем
 * стеке у мобов была своя арифметика, и сравнить два числа было нельзя.
 */
public final class MobService {

    /** Имя источника статов моба. */
    public static final String STAT_SOURCE = "mob";

    private final Plugin plugin;
    private final NamespacedKey idKey;
    private final MobRegistry registry;
    private final StatService stats;
    private final SkillRegistry skills;
    private final SkillRuntime runtime;
    private final DoubleSupplier random;

    public MobService(Plugin plugin, MobRegistry registry, StatService stats,
                      SkillRegistry skills, SkillRuntime runtime, DoubleSupplier random) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "mob");
        this.registry = registry;
        this.stats = stats;
        this.skills = skills;
        this.runtime = runtime;
        this.random = random;
    }

    public MobRegistry registry() {
        return registry;
    }

    /** Идентификатор нашего моба, если это он. */
    public Optional<String> idOf(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entity.getPersistentDataContainer()
                .get(idKey, PersistentDataType.STRING));
    }

    public Optional<MobDef> defOf(Entity entity) {
        return idOf(entity).flatMap(registry::find);
    }

    /** Создаёт моба в мире. */
    public Optional<LivingEntity> spawn(String mobId, Location where) {
        Optional<MobDef> def = registry.find(mobId);
        if (def.isEmpty() || where.getWorld() == null) {
            return Optional.empty();
        }
        EntityType type = resolveType(def.get());
        if (type == null) {
            return Optional.empty();
        }
        Entity spawned = where.getWorld().spawnEntity(where, type);
        if (!(spawned instanceof LivingEntity living)) {
            spawned.remove();
            plugin.getLogger().warning("тип " + def.get().entityType() + " не живое существо");
            return Optional.empty();
        }
        apply(def.get(), living);
        return Optional.of(living);
    }

    /**
     * Делает уже появившееся существо нашим мобом.
     *
     * <p>Отдельный путь нужен для подмены естественного спавна: там существо
     * создаёт сервер, и пересоздавать его значило бы потерять всё, что он уже
     * решил, — от места до причины появления.
     */
    public void apply(MobDef def, LivingEntity living) {
        living.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());
        living.customName(Component.text(def.display()));
        living.setCustomNameVisible(true);
        living.setGlowing(def.glowing());
        if (living instanceof Ageable ageable) {
            if (def.baby()) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }
        }

        var maxHealth = living.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(def.health());
        }
        living.setHealth(def.health());

        if (def.damage() > 0) {
            var attack = living.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
            if (attack != null) {
                attack.setBaseValue(def.damage());
            }
        }

        // Статы моба — источник надбавок, как у снаряжения игрока.
        stats.setSource(living.getUniqueId(), STAT_SOURCE, def.modifiers(STAT_SOURCE));

        fire(def, living, MobTrigger.ON_SPAWN, null);
    }

    /** Забывает моба: умер или выгрузился. */
    public void forget(UUID mob) {
        stats.forget(mob);
    }

    /**
     * Запускает навыки моба на этот триггер.
     *
     * <p>Вероятность проверяется здесь, а не внутри навыка: «сорок процентов, что
     * ударит» — свойство моба, а не навыка, и тот же навык у другого моба может
     * срабатывать всегда.
     */
    public void fire(MobDef def, LivingEntity mob, MobTrigger trigger, UUID source) {
        for (MobSkill skill : def.skillsOn(trigger)) {
            if (skill.chance() < 100 && random.getAsDouble() * 100 >= skill.chance()) {
                continue;
            }
            skills.find(skill.skillId()).ifPresent(found -> runtime.cast(
                    new CastContext(mob.getUniqueId(), 1, null, source), found, 0));
        }
    }

    /** Периодические навыки всех наших мобов: вызывается таймером. */
    public void tick(long tick) {
        for (var world : Bukkit.getWorlds()) {
            for (LivingEntity living : world.getLivingEntities()) {
                Optional<MobDef> def = defOf(living);
                if (def.isEmpty() || living.isDead()) {
                    continue;
                }
                for (MobSkill skill : def.get().skillsOn(MobTrigger.ON_INTERVAL)) {
                    if (tick % skill.intervalTicks() != 0) {
                        continue;
                    }
                    if (skill.chance() < 100 && random.getAsDouble() * 100 >= skill.chance()) {
                        continue;
                    }
                    skills.find(skill.skillId()).ifPresent(found -> runtime.cast(
                            new CastContext(living.getUniqueId(), 1, null, null), found, 0));
                }
            }
        }
    }

    private EntityType resolveType(MobDef def) {
        try {
            return EntityType.valueOf(def.entityType());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("неизвестный тип существа у моба " + def.id()
                    + ": " + def.entityType());
            return null;
        }
    }

    /** Все наши живые мобы: нужно для отладки и для команды подсчёта. */
    public List<LivingEntity> living() {
        List<LivingEntity> out = new java.util.ArrayList<>();
        for (var world : Bukkit.getWorlds()) {
            for (LivingEntity living : world.getLivingEntities()) {
                if (idOf(living).isPresent()) {
                    out.add(living);
                }
            }
        }
        return out;
    }
}
