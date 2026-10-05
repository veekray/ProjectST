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

    /** Урон по целям шага. Проходит через единый конвейер. */
    record Damage(NumberRef amount, DamageSchool school) implements Action {
        @Override
        public String name() {
            return "damage";
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

    /** Снятие статуса с целей шага. */
    record RemoveStatus(String statusId) implements Action {
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
     */
    record ModifyStat(String statId, StatOp op, NumberRef value, NumberRef duration)
            implements Action {
        public ModifyStat {
            if (statId == null || statId.isBlank()) {
                throw new IllegalArgumentException("statId обязателен");
            }
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
                      String particle, String onHit, String onEnd) implements Action {
        public Projectile {
            if (onHit == null || onHit.isBlank()) {
                throw new IllegalArgumentException("снаряд без onHit ничего не делает");
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
     */
    record PlaceZone(String tag, NumberRef radius, NumberRef duration, boolean atOrigin,
                     String particle) implements Action {
        public PlaceZone {
            if (tag == null || tag.isBlank()) {
                throw new IllegalArgumentException("у зоны обязателен тег");
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

    /** Сообщение целям шага. */
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
