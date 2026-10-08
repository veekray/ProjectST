package ru.projectst.rpgcore.skill;

import ru.projectst.rpgcore.damage.DamageSchool;
import ru.projectst.rpgcore.stat.StatOp;

/**
 * Примитив, который умеет делать навык.
 *
 * <p>Закрытый набор: новый примитив добавляется сюда и в исполнитель, то есть
 * компилятором. Это и есть обещание «если механики не хватает — она
 * добавляется в код, а не обходится костылём».
 *
 * <p>Список короткий намеренно. В старом стеке механик было несколько сотен, и
 * половина делала почти то же самое чуть иначе: три способа нанести урон, пять
 * способов сдвинуть цель, два вида заморозки, из которых один не морозил.
 * Здесь на каждое действие ровно один примитив, и его имя означает то, что он
 * делает.
 */
public sealed interface Action {

    /** Имя для сообщений об ошибках и отладки. */
    String name();

    // ------------------------------------------------------------------ бой

    /**
     * От чего считается урон.
     *
     * <p>Доля здоровья — отдельная основа, а не отдельное действие: иначе
     * «нанести урон» было бы двумя разными путями в конвейере, и однажды один
     * из них перестал бы учитывать защиту или крит. Основа решает только, какое
     * число войдёт в конвейер; дальше всё одинаково.
     */
    enum Basis {
        /** Число как написано. */
        FLAT,
        /** Доля от предела здоровья цели: толстая жертва получает больше. */
        TARGET_MAX_HEALTH,
        /** Доля от текущего здоровья цели: добивание не докручивает раненого. */
        TARGET_CURRENT_HEALTH
    }

    /** Урон по целям шага. Проходит через единый конвейер. */
    record Damage(NumberRef amount, DamageSchool school, Basis basis) implements Action {

        public Damage(NumberRef amount, DamageSchool school) {
            this(amount, school, Basis.FLAT);
        }

        public Damage {
            if (basis == null) {
                basis = Basis.FLAT;
            }
        }

        @Override
        public String name() {
            return "damage";
        }
    }

    /**
     * Плата здоровьем: доля предела, но никогда не насмерть.
     *
     * <p>Отдельно от урона, и это главное в ней. Плата не идёт через конвейер:
     * её не снижает броня, она не может быть критом, её не поглощает щит и — что
     * важнее всего — она <b>не считается полученным уроном</b>. Иначе
     * собственная цена навыка срывала бы разгон, будила контрудары и кормила
     * всё, что срабатывает «когда по мне попали».
     *
     * <p>Нижний предел — одна единица здоровья. Берсерк платит за силу, но не
     * платит жизнью: умереть от своей же кнопки — это не риск, а поломка.
     */
    record Sacrifice(NumberRef percent) implements Action {
        @Override
        public String name() {
            return "sacrifice";
        }
    }

    /** Лечение целей шага. */
    record Heal(NumberRef amount) implements Action {
        @Override
        public String name() {
            return "heal";
        }
    }

    // ------------------------------------------------------------------ статусы

    /**
     * Наложение статуса.
     *
     * @param duration длительность; если {@code null}, берётся из статуса
     * @param amount   полезная нагрузка: запас щита. Может быть {@code null}
     */
    record ApplyStatus(String statusId, NumberRef duration, NumberRef amount) implements Action {
        public ApplyStatus {
            if (statusId == null || statusId.isBlank()) {
                throw new IllegalArgumentException("statusId обязателен");
            }
        }

        @Override
        public String name() {
            return "status";
        }
    }

    /**
     * Снятие статуса с целей шага.
     *
     * @param stacks сколько стаков снять; ноль — статус целиком. Нужно там, где
     *               стаки это заряды, и каждый ответ тратит ровно один
     */
    /**
     * Снятие статуса: по имени или по метке.
     *
     * <p>По метке нужно там, где снимают «что-нибудь полезное», не зная заранее
     * что: Карманник крадёт у цели усиление, а какое именно — зависит от того,
     * что на ней висит. Перечислять все усиления в файле навыка значило бы
     * забыть новое в тот же день, когда его добавят.
     *
     * @param statusId имя статуса; пусто, если снимают по метке
     * @param tag      метка; пусто, если снимают по имени
     * @param stacks   сколько стаков; ноль — весь статус целиком
     */
    record RemoveStatus(String statusId, String tag, int stacks) implements Action {

        public RemoveStatus(String statusId) {
            this(statusId, null, 0);
        }

        public RemoveStatus(String statusId, int stacks) {
            this(statusId, null, stacks);
        }

        public RemoveStatus {
            boolean byId = statusId != null && !statusId.isBlank();
            boolean byTag = tag != null && !tag.isBlank();
            if (byId == byTag) {
                throw new IllegalArgumentException(
                        "снятие статуса задаётся либо именем, либо меткой, но не обоими");
            }
        }

        @Override
        public String name() {
            return "remove-status";
        }
    }

    /**
     * Временная надбавка к стату.
     *
     * <p>Замена {@code ml tempstat} из старого стека, но без строковой команды:
     * опечатка в имени стата здесь — ошибка связывания, а не молчаливое
     * бездействие.
     *
     * <p>Со {@code status} надбавка — часть статуса: живёт, пока статус лежит на
     * цели, снимается вместе с ним и видна игроку на его значке. Своей
     * длительности у неё тогда нет — два срока у одного эффекта однажды
     * разошлись бы.
     *
     * @param statusId к какому статусу привязана; {@code null} — сама по себе,
     *                 со своим сроком
     */
    record ModifyStat(String statId, StatOp op, NumberRef value, NumberRef duration,
                      String statusId) implements Action {
        public ModifyStat {
            if (statId == null || statId.isBlank()) {
                throw new IllegalArgumentException("statId обязателен");
            }
            statusId = statusId == null || statusId.isBlank() ? null : statusId;
            if (statusId != null && duration != null) {
                throw new IllegalArgumentException(
                        "надбавка со статусом живёт его сроком: duration не нужен");
            }
        }

        public ModifyStat(String statId, StatOp op, NumberRef value, NumberRef duration) {
            this(statId, op, value, duration, null);
        }

        @Override
        public String name() {
            return "modify-stat";
        }
    }

    /** Ванильный эффект зелья: слепота, замедление, яд. */
    record Potion(String effect, NumberRef duration, int amplifier) implements Action {
        public Potion {
            if (effect == null || effect.isBlank()) {
                throw new IllegalArgumentException("effect обязателен");
            }
        }

        @Override
        public String name() {
            return "potion";
        }
    }

    /** Снять эффект зелья с целей шага. */
    record ClearPotion(String effect) implements Action {
        public ClearPotion {
            if (effect == null || effect.isBlank()) {
                throw new IllegalArgumentException("effect обязателен");
            }
        }

        @Override
        public String name() {
            return "clear-potion";
        }
    }

    // ------------------------------------------------------------------ движение

    /**
     * Отбросить цели от кастера.
     *
     * <p>Единственный примитив отбрасывания. В старом стеке их было несколько,
     * и они по-разному считали силу: {@code pull} делил velocity на десять, а
     * {@code throw} нет, из-за чего одинаковые на вид числа давали разный
     * результат.
     */
    record Push(NumberRef strength, NumberRef lift) implements Action {
        @Override
        public String name() {
            return "push";
        }
    }

    /**
     * Притянуть цели к точке действия, а если её нет — к кастеру.
     *
     * @param ticks на сколько тиков растянуть притяжение. Несколько слабых
     *              импульсов вместо одного сильного: один проносит цель мимо
     *              точки, а каждый следующий пересчитывает направление и
     *              перелёт сам себя исправляет
     */
    record Pull(NumberRef strength, int ticks) implements Action {
        @Override
        public String name() {
            return "pull";
        }
    }

    /**
     * Рывок кастера: по взгляду или по ходу.
     *
     * <p>Отдельно от {@link Push}: тот отбрасывает чужих от точки, а этот
     * двигает самого кастера. Разные вещи с разными именами — в старом стеке и
     * то и другое делалось механикой «velocity», и чтобы понять, кого именно она
     * двигает, надо было смотреть таргетер.
     *
     * @param alongMovement рывок туда, куда игрок идёт, а не куда смотрит.
     *                      Нужно уклонению: бегущий влево и смотрящий на врага
     *                      хочет уйти влево, а не прыгнуть на врага. Если
     *                      направления хода нет — игрок стоит на месте — рывок
     *                      идёт по взгляду: единственная замена, которая не
     *                      выглядит поломкой
     */
    record Dash(NumberRef strength, NumberRef lift, boolean alongMovement) implements Action {
        @Override
        public String name() {
            return "dash";
        }
    }

    /**
     * Сближение: кастер оказывается рядом с целью шага.
     *
     * @param distance на сколько блоков отступить от цели
     * @param behind   true — за спину цели, false — перед ней
     */
    record Approach(NumberRef distance, boolean behind) implements Action {
        @Override
        public String name() {
            return "approach";
        }
    }

    /**
     * Переместить цели.
     *
     * @param forward на сколько блоков вперёд по взгляду; при точке действия
     *                перемещение идёт в неё, а это значение игнорируется
     */
    record Teleport(NumberRef forward) implements Action {
        @Override
        public String name() {
            return "teleport";
        }
    }

    // ------------------------------------------------------------------ видимое

    /**
     * Частицы.
     *
     * @param shape  форма: точка, сфера, кольцо, линия до цели
     * @param atOrigin рисовать в точке действия, а не на целях
     */
    record Particles(String particle, Shape shape, NumberRef count, NumberRef size,
                     boolean atOrigin) implements Action {

        public enum Shape { POINT, SPHERE, RING, LINE }

        @Override
        public String name() {
            return "particles";
        }
    }

    record Sound(String sound, double volume, double pitch, boolean atOrigin) implements Action {
        @Override
        public String name() {
            return "sound";
        }
    }

    /**
     * Летящий снаряд.
     *
     * <p>Отдельно от {@link Ray} и назван иначе, потому что ведёт себя иначе:
     * его видно, его можно обогнать, и он может никуда не попасть. В старом
     * стеке «projectile» и мгновенный луч настраивались почти одинаковыми
     * ключами, и перепутать их было проще, чем заметить разницу.
     *
     * @param onHit навык в точке попадания; цель становится его trigger
     * @param onEnd навык в точке, где снаряд закончился, никого не задев;
     *              {@code null} — ничего. Явный второй ключ, а не флаг: промах
     *              и попадание — разные события
     */
    record Projectile(NumberRef speed, NumberRef range, NumberRef hitRadius, NumberRef gravity,
                      int pierce, boolean hitPlayers, boolean hitMobs, boolean stopAtBlock,
                      String particle, String onHit, String onEnd, double yawOffset)
            implements Action {

        public Projectile(NumberRef speed, NumberRef range, NumberRef hitRadius,
                          NumberRef gravity, int pierce, boolean hitPlayers, boolean hitMobs,
                          boolean stopAtBlock, String particle, String onHit, String onEnd) {
            this(speed, range, hitRadius, gravity, pierce, hitPlayers, hitMobs, stopAtBlock,
                    particle, onHit, onEnd, 0);
        }

        public Projectile {
            // Хотя бы один из двух: иначе снаряд летит и ничего не делает.
            // Снаряд только с onEnd — законный случай: так ставят метку там,
            // куда он упал, никого не задевая.
            if ((onHit == null || onHit.isBlank()) && (onEnd == null || onEnd.isBlank())) {
                throw new IllegalArgumentException("снаряду нужен on-hit или on-end");
            }
        }

        @Override
        public String name() {
            return "projectile";
        }
    }

    // ------------------------------------------------------------------ призыв

    /**
     * Призывает существо.
     *
     * <p>Владелец записывается сразу, при постановке на учёт, а не штампуется
     * обработчиком спавна. В старом стеке именно это опаздывало: заявка на
     * владение уходила раньше, чем моб появлялся, и зверь оставался бесхозным —
     * то есть своим для любого друида на карте.
     *
     * @param mob      тип существа
     * @param count    сколько призвать
     * @param duration сколько тиков живёт
     * @param health   здоровье; ноль — штатное для типа
     * @param tag      по нему навыки находят своих призванных
     * @param attacksEnemies само ищет врагов владельца
     * @param atOrigin призывать в точке действия, а не у целей шага
     */
    record Summon(String mob, NumberRef count, NumberRef duration, NumberRef health,
                  String tag, boolean attacksEnemies, boolean atOrigin) implements Action {
        public Summon {
            if (mob == null || mob.isBlank()) {
                throw new IllegalArgumentException("нужен тип существа");
            }
            if (tag == null || tag.isBlank()) {
                throw new IllegalArgumentException("у призванного обязателен тег");
            }
        }

        @Override
        public String name() {
            return "summon";
        }
    }

    /** Снимает своих призванных с этим тегом: отзыв зверя, конец эффекта. */
    record Dismiss(String tag) implements Action {
        public Dismiss {
            if (tag == null || tag.isBlank()) {
                throw new IllegalArgumentException("нужен тег призванных");
            }
        }

        @Override
        public String name() {
            return "dismiss";
        }
    }

    // ------------------------------------------------------------------ зоны

    /**
     * Оставить зону: печать, круг, ловушку.
     *
     * @param tag      тип зоны, по которому её потом ищут
     * @param atOrigin ставить в точке действия, а не на целях шага
     * @param particle чем рисуется; пусто — невидимая
     * @param minGap   не ставить, если своя зона с этим тегом уже ближе этого
     *                 расстояния. Без такой проверки повторные касты на месте
     *                 складывали бы печати в одну точку, и финишер, который
     *                 считает печати, получал бы их пачкой за бесплатно
     * @param onEnter  навык, который выполняется, когда в зону кто-то вошёл;
     *                 вошедший становится его trigger
     * @param onTick   навык, который зона выполняет сама через tickInterval
     * @param tickInterval промежуток между тиками зоны
     */
    record PlaceZone(String tag, NumberRef radius, NumberRef duration, boolean atOrigin,
                     String particle, NumberRef minGap, String onEnter, String onTick,
                     int tickInterval) implements Action {

        public PlaceZone(String tag, NumberRef radius, NumberRef duration, boolean atOrigin,
                         String particle) {
            this(tag, radius, duration, atOrigin, particle, null, null, null, 0);
        }
        public PlaceZone {
            if (tag == null || tag.isBlank()) {
                throw new IllegalArgumentException("у зоны обязателен тег");
            }
            if (onTick != null && tickInterval < 1) {
                throw new IllegalArgumentException("тикающей зоне нужен промежуток");
            }
        }

        @Override
        public String name() {
            return "zone";
        }
    }

    /**
     * Снять зоны рядом и записать их число в счётчик каста.
     *
     * <p>Снятие и подсчёт — одно действие, потому что раздельно они разошлись бы:
     * посчитать печати, а снять другие.
     *
     * @param counter куда записать число снятых
     * @param ownOnly снимать только свои зоны
     */
    record ConsumeZones(String tag, NumberRef radius, String counter, boolean ownOnly,
                        boolean atOrigin) implements Action {
        public ConsumeZones {
            if (tag == null || tag.isBlank()) {
                throw new IllegalArgumentException("у зоны обязателен тег");
            }
            if (counter == null || counter.isBlank()) {
                throw new IllegalArgumentException("нужно имя счётчика");
            }
        }

        @Override
        public String name() {
            return "consume-zones";
        }
    }

    // ------------------------------------------------------------------ ресурс и счёт

    /**
     * Вернуть кастеру его ресурс: ману магу, выносливость плуту.
     *
     * <p>Действие применяется по разу на каждую цель шага, поэтому «за каждое
     * попадание» пишется само собой и не требует отдельного ключа.
     */
    record Restore(NumberRef amount) implements Action {
        @Override
        public String name() {
            return "restore";
        }
    }

    /**
     * Записать число в счётчик каста: сколько целей у шага, а с ключом status —
     * сколько стаков этого статуса на кастере.
     *
     * <p>Единственный способ узнать, сколько целей нашлось, — и он явный. В
     * старом стеке счёт целей приходилось изображать аурой со стаками, которую
     * накручивал {@code sudoskill}, потому что тело метаскилла выполнялось один
     * раз на весь список.
     */
    record Count(String counter, String statusId) implements Action {

        public Count(String counter) {
            this(counter, null);
        }

        public Count {
            if (counter == null || counter.isBlank()) {
                throw new IllegalArgumentException("нужно имя счётчика");
            }
        }

        @Override
        public String name() {
            return "count";
        }
    }

    /** Сообщение целям шага. */
    /**
     * Обмен местами с целью.
     *
     * <p>Не два телепорта подряд: между ними цель успела бы оказаться в точке,
     * куда уже перенесли кастера, и один из двоих застревал бы в блоке. Обмен
     * считается от обеих позиций сразу.
     */
    /**
     * Сбить цель: существо выбирает себе другую жертву рядом.
     *
     * <p>Работает по мобам. Отнимать управление у игрока таким способом нельзя:
     * это контроль, а контроль в этом ядре делается статусами, которые видно на
     * экране и у которых объявлены конфликты.
     */
    record Confuse(NumberRef radius) implements Action {
        @Override
        public String name() {
            return "confuse";
        }
    }

    /**
     * Подсветка контуром сквозь стены.
     *
     * <p>Чистая подсказка глазам, ничего не меняющая в бою: ею охотник метит
     * добычу и показывает её всей группе. Цвет не задаётся — в Minecraft он
     * берётся у команды таблицы, а командами на сервере распоряжаются и другие
     * плагины, и спорить с ними из-за оттенка контура незачем.
     */
    record Glow(NumberRef duration) implements Action {
        @Override
        public String name() {
            return "glow";
        }
    }

    /** Отправляет щит цели в перезарядку: прикрыться ближайшие секунды нечем. */
    record DisableShield(NumberRef duration) implements Action {
        @Override
        public String name() {
            return "disable-shield";
        }
    }

    record Swap() implements Action {
        @Override
        public String name() {
            return "swap";
        }
    }

    /**
     * Раскидать цель в случайную точку рядом.
     *
     * <p>Точка берётся заново для каждой цели: иначе строй не рассыпается, а
     * переезжает целиком, и смысл теряется.
     */
    record Scatter(NumberRef radius) implements Action {
        @Override
        public String name() {
            return "scatter";
        }
    }

    /**
     * Сбросить перезарядку навыка у кастера.
     *
     * <p>Имя навыка проверяет компоновщик: сброс несуществующей перезарядки
     * молча не делал бы ничего, а это ровно тот класс ошибок, ради которого всё
     * это и затевалось.
     */
    record ResetCooldown(String skillId) implements Action {
        public ResetCooldown {
            if (skillId == null || skillId.isBlank()) {
                throw new IllegalArgumentException("skillId обязателен");
            }
        }

        @Override
        public String name() {
            return "reset-cooldown";
        }
    }

    /**
     * Заставить существ вокруг забыть цель.
     *
     * <p>Работает по тем, кто вообще умеет кого-то выбирать целью, то есть по
     * мобам. На игроков не действует и действовать не должно: отнимать у игрока
     * управление — это уже контроль, а он делается статусами.
     */
    record ClearThreat(NumberRef radius) implements Action {
        @Override
        public String name() {
            return "clear-threat";
        }
    }

    record Message(String text) implements Action {
        @Override
        public String name() {
            return "message";
        }
    }

    // ------------------------------------------------------------------ составное

    /**
     * Запустить другой навык.
     *
     * <p>Так собираются ветки: усиленный и обычный варианты — это отдельные
     * навыки, а условие выбирает между ними. Существование навыка проверяется
     * связыванием, в отличие от {@code skill{s=...}} старого стека, где ссылка
     * в пустоту просто ничего не делала.
     *
     * @param atTargets true — выполнить от лица каждой цели шага; иначе от
     *                  лица кастера, а цели шага станут точкой действия
     */
    record Cast(String skillId, boolean atTargets) implements Action {
        public Cast {
            if (skillId == null || skillId.isBlank()) {
                throw new IllegalArgumentException("skillId обязателен");
            }
        }

        @Override
        public String name() {
            return "cast";
        }
    }

    /**
     * Мгновенный луч по направлению взгляда.
     *
     * <p>Назван {@code ray}, а не {@code projectile}, намеренно: он не летит, а
     * попадает сразу. Имя обязано означать то, что примитив делает — это одно
     * из правил, ради которых проект затевался. Летящий снаряд появится
     * отдельным примитивом и будет называться иначе.
     *
     * @param onHit навык, который выполняется в точке попадания
     */
    record Ray(NumberRef range, String onHit, boolean stopAtEntity) implements Action {
        public Ray {
            if (onHit == null || onHit.isBlank()) {
                throw new IllegalArgumentException("луч без onHit ничего не делает");
            }
        }

        @Override
        public String name() {
            return "ray";
        }
    }
}
