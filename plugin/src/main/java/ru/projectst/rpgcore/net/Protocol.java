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
 * <p><b>Клиент просит, сервер решает.</b> С версии 2 мод умеет присылать
 * действия: применить слот, изучить навык, вложить очко, занять слот, выбрать
 * класс. Ни одно из них не выполняется «потому что клиент так сказал» — каждое
 * идёт через те же службы, что и команда в чате, со всеми их проверками и
 * отказами. Злонамеренный клиент может попросить ровно то, что игрок может
 * набрать руками, и получит тот же ответ.
 *
 * <p><b>Мод обязателен</b>: без рукопожатия игрока не пускают на сервер
 * (ModGate). Но правила всё равно живут только на сервере. Канал — способ
 * показать и попросить, а не способ что-то сделать: меню открывается и
 * командой, навык применяется и через {@code /rpg slot}, и игрок, оставшийся
 * без мода, теряет удобство, а не возможности.
 */
public final class Protocol {

    /**
     * Версия формата.
     *
     * <p>2 — счётчики ядра класса, данные меню и действия от клиента.
     * <p>3 — описание навыка и наибольший урон за попадание: подсказке в меню
     * нужно и то, и другое, а выводить их на клиенте значило бы считать баланс
     * второй раз.
     */
    public static final int VERSION = 4;

    /** Клиент говорит, что он есть, и называет свою версию формата. */
    public static final String CHANNEL_HELLO = "rpgcore:hello";

    /** Сервер отвечает на рукопожатие: принято или отказано. */
    public static final String CHANNEL_WELCOME = "rpgcore:welcome";

    /** Сервер присылает состояние игрока. */
    public static final String CHANNEL_STATE = "rpgcore:state";

    /** Сервер присылает всё, что нужно меню: классы, навыки, числа. */
    public static final String CHANNEL_MENU = "rpgcore:menu";

    /** Клиент просит действие. */
    public static final String CHANNEL_ACTION = "rpgcore:action";

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

    /**
     * Что просит клиент.
     *
     * <p>Набор закрытый и маленький намеренно: канал, через который можно
     * попросить «что угодно», однажды попросят не то.
     */
    public enum Action {
        /** Применить навык из слота. */
        CAST_SLOT,
        /** Изучить навык. */
        UNLOCK,
        /** Вложить очко в уровень навыка. */
        UPGRADE,
        /** Занять слот навыком. */
        BIND,
        /** Освободить слот. */
        UNBIND,
        /** Выбрать класс. */
        CHOOSE_CLASS,
        /** Прислать данные меню заново. */
        REFRESH_MENU;

        public int code() {
            return ordinal();
        }

        public static Action of(int code) {
            Action[] values = values();
            return code >= 0 && code < values.length ? values[code] : REFRESH_MENU;
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
