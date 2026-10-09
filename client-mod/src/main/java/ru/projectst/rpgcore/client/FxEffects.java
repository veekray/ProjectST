package ru.projectst.rpgcore.client;

import java.util.List;
import ru.projectst.rpgcore.net.FxMessage;

/**
 * Живые эффекты навыков на клиенте.
 *
 * <p>Пока только принимает события: канал должен быть зарегистрирован раньше,
 * чем появится рисование, иначе мод новой версии получал бы сообщения, которых
 * не знает.
 */
public final class FxEffects {

    private FxEffects() {
    }

    /** События одного сообщения с сервера, в главном потоке клиента. */
    static void accept(List<FxMessage.Event> events) {
    }

    /** Выход с сервера: всё живое забывается. */
    static void clear() {
    }
}
