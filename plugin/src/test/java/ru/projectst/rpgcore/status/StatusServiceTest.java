package ru.projectst.rpgcore.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.projectst.rpgcore.damage.DefenderState;

/**
 * Матрица по объявленным отношениям. Каждое отношение проверяется и в
 * положительном, и в отрицательном случае: иначе тест «блокировка работает»
 * прошёл бы и при реализации «ничего никогда не накладывается».
 */
class StatusServiceTest {

    private static final UUID TARGET = UUID.randomUUID();

    /** Управляемый тик: время двигаем руками, иначе тесты зависели бы от сервера. */
    private long tick;

    private StatusService service(StatusDef... defs) {
        Map<String, StatusDef> map = new LinkedHashMap<>();
        for (StatusDef d : defs) {
            map.put(d.id(), d);
        }
        tick = 0;
        return new StatusService(new StatusRegistry(map), () -> tick);
    }

    private static StatusDef def(String id, StatusCategory category) {
        return new StatusDef(id, category, 40, 1, Stacking.REFRESH, 0, null,
                Set.of(), Set.of(), Set.of(), Set.of());
    }

    private static StatusDef def(String id, StatusCategory category, int priority,
                                 StatusCategory exclusive, Set<String> suppresses,
                                 Set<String> removes, Set<String> blocks) {
        return new StatusDef(id, category, 40, 1, Stacking.REFRESH, priority, exclusive,
                suppresses, removes, blocks, Set.of());
    }

    private static StatusApplication apply(String id) {
        return StatusApplication.of(id, "test");
    }

    // ------------------------------------------------------------------ присяга

    private static StatusDef challenge() {
        return new StatusDef("challenge", StatusCategory.DEBUFF, 200, 1, Stacking.REFRESH,
                0, null, Set.of(), Set.of(), Set.of(), Set.of("challenge"));
    }

    @Test
    @DisplayName("вызванный бьёт вызвавшего в полную силу")
    void challengerTakesFullHit() {
        StatusService s = service(challenge());
        UUID knight = UUID.randomUUID();
        s.apply(TARGET, new StatusApplication("challenge", 200, 50,
                "skill:knight_challenge:" + knight));

        assertEquals(1.0, s.challengeScale(TARGET, knight), 1e-9,
                "иначе рыцарь наказывал бы врага за то, что тот принял вызов");
    }

    @Test
    @DisplayName("вызванный бьёт всех прочих вполсилы")
    void othersTakeHalf() {
        StatusService s = service(challenge());
        UUID knight = UUID.randomUUID();
        UUID mage = UUID.randomUUID();
        s.apply(TARGET, new StatusApplication("challenge", 200, 50,
                "skill:knight_challenge:" + knight));

        assertEquals(0.5, s.challengeScale(TARGET, mage), 1e-9);
    }

    @Test
    @DisplayName("без вызова ничего не ослабляется")
    void noChallengeNoScale() {
        StatusService s = service(challenge());
        assertEquals(1.0, s.challengeScale(TARGET, UUID.randomUUID()), 1e-9);
    }

    @Test
    @DisplayName("величина берётся из статуса, а не из кода")
    void amountComesFromContent() {
        StatusService s = service(challenge());
        s.apply(TARGET, new StatusApplication("challenge", 200, 100,
                "skill:knight_fortress:" + UUID.randomUUID()));

        assertEquals(0.0, s.challengeScale(TARGET, UUID.randomUUID()), 1e-9,
                "сто процентов — это ульта рыцаря, и она тоже живёт в содержимом");
    }

    // ------------------------------------------------------------------ базовое

    @Test
    @DisplayName("обычное наложение действует")
    void plainApply() {
        StatusService s = service(def("stun", StatusCategory.CONTROL));
        StatusOutcome out = s.apply(TARGET, apply("stun"));

        assertEquals(StatusOutcome.Kind.APPLIED, out.kind());
        assertTrue(out.active());
        assertTrue(s.isActing(TARGET, "stun"));
    }

    @Test
    @DisplayName("незаявленный статус — исключение, а не тихий отказ")
    void unknownStatusThrows() {
        StatusService s = service(def("stun", StatusCategory.CONTROL));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> s.apply(TARGET, apply("нет_такого")));
    }

    @Test
    @DisplayName("статус истекает по своему сроку")
    void statusExpires() {
        StatusService s = service(def("stun", StatusCategory.CONTROL));
        s.apply(TARGET, apply("stun"));

        tick = 39;
        assertTrue(s.isActing(TARGET, "stun"));
        tick = 40;
        assertFalse(s.isActing(TARGET, "stun"));
    }

    // ------------------------------------------------------------------ blocks

    @Test
    @DisplayName("blocks: активный не даёт наложить перечисленный")
    void blocksPreventsApply() {
        StatusService s = service(
                def("immunity", StatusCategory.IMMUNITY, 0, null, Set.of(), Set.of(), Set.of("stun")),
                def("stun", StatusCategory.CONTROL));
        s.apply(TARGET, apply("immunity"));

        StatusOutcome out = s.apply(TARGET, apply("stun"));

        assertEquals(StatusOutcome.Kind.BLOCKED, out.kind());
        assertEquals("immunity", out.other());
        assertEquals("blocks", out.rule());
        assertFalse(s.isPresent(TARGET, "stun"));
    }

    @Test
    @DisplayName("blocks: без блокирующего тот же статус накладывается")
    void blocksDoesNotPreventWhenAbsent() {
        StatusService s = service(
                def("immunity", StatusCategory.IMMUNITY, 0, null, Set.of(), Set.of(), Set.of("stun")),
                def("stun", StatusCategory.CONTROL));

        assertEquals(StatusOutcome.Kind.APPLIED, s.apply(TARGET, apply("stun")).kind());
    }

    // ------------------------------------------------------------------ suppresses

    @Test
    @DisplayName("suppresses: подавленный наложен, но не действует")
    void suppressedIsPresentButNotActing() {
        StatusService s = service(
                def("banish", StatusCategory.IMMUNITY, 0, null, Set.of("stun"), Set.of(), Set.of()),
                def("stun", StatusCategory.CONTROL));
        s.apply(TARGET, apply("banish"));

        StatusOutcome out = s.apply(TARGET, apply("stun"));

        assertEquals(StatusOutcome.Kind.APPLIED_SUPPRESSED, out.kind());
        assertEquals("banish", out.other());
        assertTrue(out.succeeded(), "наложен");
        assertFalse(out.active(), "но не действует");
        assertTrue(s.isPresent(TARGET, "stun"));
        assertFalse(s.isActing(TARGET, "stun"));
        assertEquals("banish", s.suppressedBy(TARGET, "stun").orElseThrow());
    }

    @Test
    @DisplayName("suppresses: подавленный продолжает тикать и истекает сам")
    void suppressedStillExpires() {
        StatusService s = service(
                def("banish", StatusCategory.IMMUNITY, 0, null, Set.of("stun"), Set.of(), Set.of()),
                def("stun", StatusCategory.CONTROL));
        s.apply(TARGET, apply("banish"));
        s.apply(TARGET, apply("stun"));

        tick = 40;
        assertFalse(s.isPresent(TARGET, "stun"), "истёк по своему сроку, а не был снят");
    }

    @Test
    @DisplayName("suppresses: снятие подавляющего возвращает подавленного в строй")
    void removingSuppressorRestores() {
        StatusService s = service(
                def("banish", StatusCategory.IMMUNITY, 0, null, Set.of("stun"), Set.of(), Set.of()),
                def("stun", StatusCategory.CONTROL));
        s.apply(TARGET, apply("banish"));
        s.apply(TARGET, apply("stun"));
        assertFalse(s.isActing(TARGET, "stun"));

        s.remove(TARGET, "banish");

        assertTrue(s.isActing(TARGET, "stun"));
    }

    @Test
    @DisplayName("suppresses: подавленный сам никого не подавляет")
    void suppressedDoesNotSuppress() {
        // C подавляет B, B подавляет A. Пока C активен, B бездействует,
        // значит A должен действовать.
        StatusService s = service(
                def("c", StatusCategory.IMMUNITY, 0, null, Set.of("b"), Set.of(), Set.of()),
                def("b", StatusCategory.DEBUFF, 0, null, Set.of("a"), Set.of(), Set.of()),
                def("a", StatusCategory.CONTROL));
        s.apply(TARGET, apply("a"));
        s.apply(TARGET, apply("b"));
        assertFalse(s.isActing(TARGET, "a"), "пока b действует, a подавлен");

        s.apply(TARGET, apply("c"));

        assertFalse(s.isActing(TARGET, "b"));
        assertTrue(s.isActing(TARGET, "a"), "b подавлен, значит a свободен");
    }

    // ------------------------------------------------------------------ removes

    @Test
    @DisplayName("removes: наложение снимает перечисленные")
    void removesDeletesOthers() {
        StatusService s = service(
                def("cleanse", StatusCategory.BUFF, 0, null, Set.of(), Set.of("poison"), Set.of()),
                def("poison", StatusCategory.DEBUFF));
        s.apply(TARGET, apply("poison"));

        s.apply(TARGET, apply("cleanse"));

        assertFalse(s.isPresent(TARGET, "poison"));
    }

    // ------------------------------------------------------------------ exclusive

    @Test
    @DisplayName("exclusive: более приоритетный вытесняет менее приоритетного")
    void exclusiveHigherPriorityReplaces() {
        StatusService s = service(
                def("root", StatusCategory.CONTROL, 5, StatusCategory.CONTROL, Set.of(), Set.of(), Set.of()),
                def("stun", StatusCategory.CONTROL, 10, StatusCategory.CONTROL, Set.of(), Set.of(), Set.of()));
        s.apply(TARGET, apply("root"));

        StatusOutcome out = s.apply(TARGET, apply("stun"));

        assertEquals(StatusOutcome.Kind.REPLACED, out.kind());
        assertEquals("root", out.other());
        assertTrue(s.isActing(TARGET, "stun"));
        assertFalse(s.isPresent(TARGET, "root"));
    }

    @Test
    @DisplayName("exclusive: менее приоритетный не проходит")
    void exclusiveLowerPriorityBlocked() {
        StatusService s = service(
                def("root", StatusCategory.CONTROL, 5, StatusCategory.CONTROL, Set.of(), Set.of(), Set.of()),
                def("stun", StatusCategory.CONTROL, 10, StatusCategory.CONTROL, Set.of(), Set.of(), Set.of()));
        s.apply(TARGET, apply("stun"));

        StatusOutcome out = s.apply(TARGET, apply("root"));

        assertEquals(StatusOutcome.Kind.BLOCKED, out.kind());
        assertEquals("stun", out.other());
        assertTrue(s.isActing(TARGET, "stun"));
        assertFalse(s.isPresent(TARGET, "root"));
    }

    @Test
    @DisplayName("exclusive: категория другая — вытеснения нет")
    void exclusiveOnlyWithinCategory() {
        StatusService s = service(
                def("poison", StatusCategory.DEBUFF),
                def("stun", StatusCategory.CONTROL, 10, StatusCategory.CONTROL, Set.of(), Set.of(), Set.of()));
        s.apply(TARGET, apply("poison"));

        s.apply(TARGET, apply("stun"));

        assertTrue(s.isActing(TARGET, "poison"), "дебафф не трогаем");
        assertTrue(s.isActing(TARGET, "stun"));
    }

    // ------------------------------------------------------------------ stacking

    @Test
    @DisplayName("stacking none: повторное наложение игнорируется")
    void stackingNone() {
        StatusDef d = new StatusDef("mark", StatusCategory.MARK, 40, 1, Stacking.NONE, 0,
                null, Set.of(), Set.of(), Set.of(), Set.of());
        StatusService s = service(d);
        s.apply(TARGET, apply("mark"));
        tick = 20;

        StatusOutcome out = s.apply(TARGET, apply("mark"));

        assertEquals(StatusOutcome.Kind.IGNORED, out.kind());
        tick = 40;
        assertFalse(s.isPresent(TARGET, "mark"), "срок не продлевался");
    }

    @Test
    @DisplayName("stacking refresh: длительность сбрасывается")
    void stackingRefresh() {
        StatusService s = service(def("stun", StatusCategory.CONTROL));
        s.apply(TARGET, apply("stun"));
        tick = 30;

        assertEquals(StatusOutcome.Kind.REFRESHED, s.apply(TARGET, apply("stun")).kind());

        tick = 69;
        assertTrue(s.isActing(TARGET, "stun"));
        tick = 70;
        assertFalse(s.isActing(TARGET, "stun"));
    }

    @Test
    @DisplayName("stacking extend: длительность прибавляется к остатку")
    void stackingExtend() {
        StatusDef d = new StatusDef("bleed", StatusCategory.DEBUFF, 40, 1, Stacking.EXTEND, 0,
                null, Set.of(), Set.of(), Set.of(), Set.of());
        StatusService s = service(d);
        s.apply(TARGET, apply("bleed"));
        tick = 30;

        assertEquals(StatusOutcome.Kind.EXTENDED, s.apply(TARGET, apply("bleed")).kind());

        tick = 79;
        assertTrue(s.isActing(TARGET, "bleed"), "40 + 40 от нулевого тика");
        tick = 80;
        assertFalse(s.isActing(TARGET, "bleed"));
    }

    @Test
    @DisplayName("stacking stacks: стаки растут до максимума, дальше только продление")
    void stackingStacks() {
        StatusDef d = new StatusDef("souls", StatusCategory.BUFF, 40, 3, Stacking.STACKS, 0,
                null, Set.of(), Set.of(), Set.of(), Set.of());
        StatusService s = service(d);

        assertEquals(StatusOutcome.Kind.APPLIED, s.apply(TARGET, apply("souls")).kind());
        assertEquals(StatusOutcome.Kind.STACKED, s.apply(TARGET, apply("souls")).kind());
        assertEquals(StatusOutcome.Kind.STACKED, s.apply(TARGET, apply("souls")).kind());

        StatusOutcome atMax = s.apply(TARGET, apply("souls"));
        assertEquals(StatusOutcome.Kind.IGNORED, atMax.kind());
        assertTrue(atMax.rule().contains("максимум"), atMax.rule());
        assertEquals(3, s.all(TARGET).get(0).stacks());
    }

    // ------------------------------------------------------------------ связь с уроном

    @Test
    @DisplayName("неуязвимость попадает в состояние цели")
    void immunityReachesDamagePipeline() {
        StatusService s = service(def("banish", StatusCategory.IMMUNITY));
        s.apply(TARGET, apply("banish"));

        DefenderState state = s.defenderState(TARGET);

        assertTrue(state.immune());
    }

    @Test
    @DisplayName("подавленная неуязвимость не защищает")
    void suppressedImmunityDoesNotProtect() {
        StatusService s = service(
                def("purge", StatusCategory.DEBUFF, 0, null, Set.of("banish"), Set.of(), Set.of()),
                def("banish", StatusCategory.IMMUNITY));
        s.apply(TARGET, apply("banish"));
        s.apply(TARGET, apply("purge"));

        assertFalse(s.defenderState(TARGET).immune());
    }

    @Test
    @DisplayName("щиты складываются в общий запас")
    void shieldsSum() {
        StatusService s = service(def("shield_a", StatusCategory.SHIELD),
                def("shield_b", StatusCategory.SHIELD));
        s.apply(TARGET, StatusApplication.shield("shield_a", 40, 30, "skill:a"));
        s.apply(TARGET, StatusApplication.shield("shield_b", 40, 20, "skill:b"));

        assertEquals(50, s.defenderState(TARGET).shieldPool(), 1e-9);
    }

    @Test
    @DisplayName("списание щита расходует тот, что истечёт раньше")
    void shieldConsumptionPrefersSoonestExpiry() {
        StatusService s = service(def("short", StatusCategory.SHIELD),
                def("long", StatusCategory.SHIELD));
        s.apply(TARGET, StatusApplication.shield("short", 20, 30, "skill:a"));
        s.apply(TARGET, StatusApplication.shield("long", 400, 30, "skill:b"));

        double taken = s.consumeShield(TARGET, 30);

        assertEquals(30, taken, 1e-9);
        assertFalse(s.isPresent(TARGET, "short"), "короткий израсходован целиком");
        assertEquals(30, s.defenderState(TARGET).shieldPool(), 1e-9, "долгий цел");
    }

    @Test
    @DisplayName("списание больше запаса списывает только доступное")
    void shieldConsumptionCannotOverdraw() {
        StatusService s = service(def("shield", StatusCategory.SHIELD));
        s.apply(TARGET, StatusApplication.shield("shield", 40, 10, "skill:a"));

        assertEquals(10, s.consumeShield(TARGET, 999), 1e-9);
        assertFalse(s.isPresent(TARGET, "shield"));
    }

    @Test
    @DisplayName("повторный щит складывается с прежним, а не вытесняет его")
    void reapplyingShieldAddsToPool() {
        StatusService s = service(def("shield", StatusCategory.SHIELD));
        s.apply(TARGET, StatusApplication.shield("shield", 40, 10, "skill:a"));
        s.apply(TARGET, StatusApplication.shield("shield", 40, 50, "skill:a"));

        assertEquals(60, s.defenderState(TARGET).shieldPool(), 1e-9,
                "два щита подряд — это два щита; замена означала бы, что второй каст "
                        + "иногда ослабляет защиту, и объяснить это игроку нечем");
    }

    // ------------------------------------------------------------------ снятие

    @Test
    @DisplayName("снятие источником убирает только его статусы")
    void removeSourceRemovesOnlyItsOwn() {
        StatusService s = service(def("a", StatusCategory.DEBUFF), def("b", StatusCategory.DEBUFF));
        s.apply(TARGET, new StatusApplication("a", 0, 0, "skill:one"));
        s.apply(TARGET, new StatusApplication("b", 0, 0, "skill:two"));

        assertEquals(1, s.removeSource(TARGET, "skill:one"));

        assertFalse(s.isPresent(TARGET, "a"));
        assertTrue(s.isPresent(TARGET, "b"));
    }

    @Test
    @DisplayName("источник обязателен: иначе статус нельзя снять и объяснить")
    void sourceRequired() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new StatusApplication("stun", 0, 0, " "));
    }

    @Test
    @DisplayName("полный срок: обновление начинает его заново, продление растягивает")
    void totalFollowsRenewal() {
        StatusService s = service(def("haste", StatusCategory.BUFF),
                new StatusDef("rite", StatusCategory.BUFF, 40, 1, Stacking.EXTEND, 0, null,
                        Set.of(), Set.of(), Set.of(), Set.of()));
        s.apply(TARGET, StatusApplication.of("haste", 40, "test"));
        s.apply(TARGET, StatusApplication.of("rite", 40, "test"));

        tick = 30;
        s.apply(TARGET, StatusApplication.of("haste", 40, "test"));
        s.apply(TARGET, StatusApplication.of("rite", 40, "test"));

        var haste = s.all(TARGET).stream().filter(x -> x.id().equals("haste")).findFirst()
                .orElseThrow();
        var rite = s.all(TARGET).stream().filter(x -> x.id().equals("rite")).findFirst()
                .orElseThrow();
        assertEquals(40, haste.total(), "обновлённый срок — снова полные 40");
        assertEquals(40, haste.remaining(tick));
        assertEquals(80, rite.total(), "продлённый срок тянется от начала");
        assertEquals(0, haste.appliedAtTick(), "порядок — по первому наложению");
    }
}
