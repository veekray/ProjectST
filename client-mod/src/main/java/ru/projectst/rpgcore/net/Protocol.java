// КОПИЯ из плагина: plugin/src/main/java/ru/projectst/rpgcore/net/Protocol.java
// Менять только там и копировать сюда — см. client-mod/README.md.
package ru.projectst.rpgcore.net;

/**
 * Канал связи с клиентским модом.
 *
 * <p><b>Версия в первом байте каждого сообщения.</b> Не в рукопожатии, не в
 * настройках — в самом сообщении. Иначе клиент старой версии молча читает новый
 * формат и показывает мусор: числа не те, полоса не та, и жалоба приходит как
 * «плагин врёт». Версия в байте даёт обеим сторонам возможность отказаться
 * словами.
 *
 * <p><b>Мод ничего не решает.</b> Канал передаёт только то, что уже посчитано
 * сервером: запас ресурса, действующие статусы, перезарядки, слоты. Клиент не
 * присылает ни одного действия, кроме рукопожатия, и поэтому мод нельзя
 * использовать, чтобы что-то получить. Плагин обязан быть полностью
 * работоспособен без мода, и это не пожелание, а условие: нажатия идут через
 * {@code SkillInputListener}, а не через канал.
 */
public final class Protocol {

    /** Версия формата. Меняется при любом несовместимом изменении полей. */
    public static final int VERSION = 1;

    /** Клиент говорит, что он есть, и называет свою версию формата. */
    public static final String CHANNEL_HELLO = "rpgcore:hello";

    /** Сервер отвечает на рукопожатие: принято или отказано. */
    public static final String CHANNEL_WELCOME = "rpgcore:welcome";

    /** Сервер присылает состояние игрока. */
    public static final String CHANNEL_STATE = "rpgcore:state";

    /** Исход рукопожатия. */
    public enum Handshake {
        /** Версии совпали, состояние будет приходить. */
        ACCEPTED,
        /** Мод старее сервера: обновить мод. */
        MOD_TOO_OLD,
        /** Мод новее сервера: обновить плагин. */
        MOD_TOO_NEW;

        public int code() {
            return ordinal();
        }

        public static Handshake of(int code) {
            Handshake[] values = values();
            return code >= 0 && code < values.length ? values[code] : MOD_TOO_OLD;
        }
    }

    /** Чем ответить на рукопожатие клиента с этой версией. */
    public static Handshake decide(int clientVersion) {
        if (clientVersion == VERSION) {
            return Handshake.ACCEPTED;
        }
        return clientVersion < VERSION ? Handshake.MOD_TOO_OLD : Handshake.MOD_TOO_NEW;
    }

    private Protocol() {
    }
}
