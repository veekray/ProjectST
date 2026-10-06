package ru.projectst.rpgcore.skill;

import java.util.List;
import java.util.Locale;

/**
 * Навык целиком: логика, привязка к классу и ступени, стоимость.
 *
 * <p>Это и есть обещанное «один скилл — один файл». Всё, что раньше было
 * размазано по четырём плагинам, лежит рядом: тело в {@code steps}, ступень и
 * класс здесь же, числа — ссылками в слой баланса.
 *
 * @param id          идентификатор, он же имя файла
 * @param display     название для игрока
 * @param classId     класс, которому принадлежит навык
 * @param tier        ступень: с какой её можно открыть
 * @param resourceCost стоимость ресурса класса: маны у магов, силы духа у
     *                    остальных. Называется не «мана» именно поэтому: ключ,
     *                    названный по одному из одиннадцати классов, читается как
     *                    ложь в десяти остальных файлах
 * @param cooldown    перезарядка в секундах
 * @param steps       тело навыка
 * @param trigger     что его запускает
 * @param intervalTicks для {@link SkillTrigger#ON_INTERVAL} — как часто
 * @param description короткое описание для подсказки: что навык делает.
 *                    Числа сюда не переписываются — их считает и присылает
 *                    сервер, а переписанное число разъезжается с балансом в
 *                    первый же вечер правок
 * @param icon        чем навык выглядит в интерфейсе; имя ванильного
     *                    предмета. Лежит в контенте, а не в таблице внутри кода:
     *                    иначе добавить навык было бы нельзя без правки Java
 * @param internal    служебный навык: игрок его не изучает и не видит, он
 *                    работает у всего класса. Нужен для отдач и тиков, которые
 *                    в старом стеке висели безымянными метаскиллами и из-за
 *                    этого попадали игроку в меню наравне с настоящими
 * @param staminaCost стоимость выносливости: общего запаса, который есть у всех
 *                    независимо от класса. Отдельно от ресурса класса, потому
 *                    что это другой запас, а не другое его название
 * @param charges     сколько раз навык можно применить, не дожидаясь
 *                    перезарядки. Каждый заряд возвращается сам, через свою
 *                    перезарядку
 * @param innate      врождённый: есть у каждого игрока с первого уровня, его не
 *                    изучают и не вешают на слот. Поэтому он не принадлежит
 *                    классу — иначе «у всех» означало бы «у всех, кто выбрал
 *                    класс», а до выбора игрок остался бы без движения
 */
public record SkillDef(String id, String display, String classId, int tier,
                       NumberRef resourceCost, NumberRef cooldown, List<Step> steps,
                       SkillTrigger trigger, int intervalTicks, boolean internal, String icon,
                       List<String> description, NumberRef staminaCost, int charges,
                       boolean innate) {

    public SkillDef(String id, String display, String classId, int tier,
                    NumberRef resourceCost, NumberRef cooldown, List<Step> steps,
                    SkillTrigger trigger, int intervalTicks, boolean internal, String icon,
                    List<String> description) {
        this(id, display, classId, tier, resourceCost, cooldown, steps, trigger, intervalTicks,
                internal, icon, description, new NumberRef.Literal(0), 1, false);
    }

    public SkillDef(String id, String display, String classId, int tier,
                    NumberRef resourceCost, NumberRef cooldown, List<Step> steps,
                    SkillTrigger trigger, int intervalTicks, boolean internal, String icon) {
        this(id, display, classId, tier, resourceCost, cooldown, steps, trigger, intervalTicks,
                internal, icon, List.of());
    }

    /** Чем выглядит навык, у которого иконка не указана. */
    public static final String DEFAULT_ICON = "PAPER";

    public SkillDef(String id, String display, String classId, int tier,
                    NumberRef resourceCost, NumberRef cooldown, List<Step> steps,
                    SkillTrigger trigger, int intervalTicks) {
        this(id, display, classId, tier, resourceCost, cooldown, steps, trigger, intervalTicks,
                false, DEFAULT_ICON);
    }

    public SkillDef(String id, String display, String classId, int tier,
                    NumberRef resourceCost, NumberRef cooldown, List<Step> steps,
                    SkillTrigger trigger, int intervalTicks, boolean internal) {
        this(id, display, classId, tier, resourceCost, cooldown, steps, trigger, intervalTicks,
                internal, DEFAULT_ICON);
    }

    /** Навык, который применяют вручную: самый частый случай. */
    public SkillDef(String id, String display, String classId, int tier,
                    NumberRef resourceCost, NumberRef cooldown, List<Step> steps) {
        this(id, display, classId, tier, resourceCost, cooldown, steps, SkillTrigger.MANUAL, 0);
    }

    public SkillDef {
        if (id == null || !id.equals(id.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("id навыка должен быть в нижнем регистре: " + id);
        }
        if (tier < 1 || tier > 5) {
            throw new IllegalArgumentException("ступень навыка от 1 до 5: " + id);
        }
        steps = steps == null ? List.of() : List.copyOf(steps);
        trigger = trigger == null ? SkillTrigger.MANUAL : trigger;
        icon = icon == null || icon.isBlank() ? DEFAULT_ICON : icon.toUpperCase(Locale.ROOT);
        description = description == null ? List.of() : List.copyOf(description);
        staminaCost = staminaCost == null ? new NumberRef.Literal(0) : staminaCost;
        if (charges < 1) {
            throw new IllegalArgumentException("зарядов у навыка не меньше одного: " + id);
        }
        if (trigger == SkillTrigger.ON_INTERVAL && intervalTicks < 1) {
            throw new IllegalArgumentException(
                    "периодическому навыку нужен промежуток: " + id);
        }
        // Врождённый навык есть у всех, поэтому принадлежать классу он не может
        // и срабатывать сам тоже: его применяет игрок нажатием.
        if (innate && !classId.isBlank()) {
            throw new IllegalArgumentException("врождённый навык без класса: " + id);
        }
        if (innate && trigger != SkillTrigger.MANUAL) {
            throw new IllegalArgumentException("врождённый навык применяют вручную: " + id);
        }
    }

    public boolean passive() {
        return trigger.passive();
    }

    /**
     * Двигает ли навык самого кастера.
     *
     * <p>Считается по телу навыка, а не объявляется ключом: ключ можно забыть
     * поставить, и тогда навык молча стал бы работать под корнями. Три действия
     * двигают кастера — рывок, телепорт и сближение, — и все три здесь, потому
     * что обездвиженному всё равно, как именно его унесло.
     */
    public boolean movesCaster() {
        for (Step step : steps) {
            for (Action action : step.actions()) {
                if (action instanceof Action.Dash
                        || action instanceof Action.Teleport
                        || action instanceof Action.Approach) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Можно ли его изучить и повесить на слот. */
    public boolean selectable() {
        return !internal && !innate && !passive();
    }
}
