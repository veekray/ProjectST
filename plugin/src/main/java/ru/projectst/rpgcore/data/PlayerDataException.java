package ru.projectst.rpgcore.data;

/**
 * Данные игрока не читаются.
 *
 * <p>Отдельное исключение, а не возврат пустых данных: подстановка пустышки
 * при ошибке формата стёрла бы прогресс игрока, и он узнал бы об этом последним.
 * Вызывающая сторона обязана решить явно — отказать во входе или починить файл.
 */
public class PlayerDataException extends RuntimeException {

    public PlayerDataException(String message) {
        super(message);
    }

    public PlayerDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
