package dev.betterchats.api.event;

import dev.betterchats.api.ApiStatus;

import java.util.function.Consumer;

/**
 * Bus de eventos de BetterChats.
 *
 * <p>{@code owner} identifica a quien se suscribe (normalmente tu instancia de
 * JavaPlugin). Al deshabilitarse ese plugin, el adaptador llama a
 * {@link #unsubscribeAll(Object)} automaticamente.</p>
 *
 * <p>Los manejadores de eventos {@link Cancellable} no se ejecutan si el evento
 * ya esta cancelado, salvo que se suscriban con {@code receiveCancelled = true}.</p>
 */
@ApiStatus.Stable
public interface EventBus {

    <T extends BetterChatsEvent> Subscription subscribe(Object owner, Class<T> type, int priority,
                                                        boolean receiveCancelled, Consumer<? super T> handler);

    default <T extends BetterChatsEvent> Subscription subscribe(Object owner, Class<T> type, int priority,
                                                                Consumer<? super T> handler) {
        return subscribe(owner, type, priority, false, handler);
    }

    default <T extends BetterChatsEvent> Subscription subscribe(Object owner, Class<T> type,
                                                                Consumer<? super T> handler) {
        return subscribe(owner, type, EventPriority.NORMAL, false, handler);
    }

    void unsubscribeAll(Object owner);

    /** Publica un evento y lo devuelve (para leer si quedo cancelado). */
    @ApiStatus.Internal
    <T extends BetterChatsEvent> T post(T event);
}
