package dev.betterchats.core.event;

import dev.betterchats.api.event.BetterChatsEvent;
import dev.betterchats.api.event.Cancellable;
import dev.betterchats.api.event.EventBus;
import dev.betterchats.api.event.Subscription;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementacion simple y thread-safe del bus. Pocas suscripciones y lectura
 * muy frecuente: lista copy-on-write ordenada por prioridad.
 */
public final class EventBusImpl implements EventBus {

    private final Logger logger;
    private final List<Handler<?>> handlers = new CopyOnWriteArrayList<>();
    private final AtomicLong order = new AtomicLong();

    public EventBusImpl(Logger logger) {
        this.logger = logger;
    }

    private final class Handler<T extends BetterChatsEvent> implements Subscription {
        final Object owner;
        final Class<T> type;
        final int priority;
        final boolean receiveCancelled;
        final Consumer<? super T> consumer;
        final long seq = order.getAndIncrement();
        volatile boolean active = true;

        Handler(Object owner, Class<T> type, int priority, boolean receiveCancelled, Consumer<? super T> consumer) {
            this.owner = owner;
            this.type = type;
            this.priority = priority;
            this.receiveCancelled = receiveCancelled;
            this.consumer = consumer;
        }

        void invoke(BetterChatsEvent event) {
            consumer.accept(type.cast(event));
        }

        @Override
        public void unsubscribe() {
            active = false;
            handlers.remove(this);
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }

    @Override
    public synchronized <T extends BetterChatsEvent> Subscription subscribe(Object owner, Class<T> type, int priority,
                                                                         boolean receiveCancelled,
                                                                         Consumer<? super T> handler) {
        Handler<T> h = new Handler<>(owner, type, priority, receiveCancelled, handler);
        handlers.add(h);
        handlers.sort(Comparator.<Handler<?>>comparingInt(x -> x.priority).thenComparingLong(x -> x.seq));
        return h;
    }

    @Override
    public void unsubscribeAll(Object owner) {
        for (Handler<?> h : handlers) {
            if (h.owner == owner || (h.owner != null && h.owner.equals(owner))) h.unsubscribe();
        }
    }

    @Override
    public <T extends BetterChatsEvent> T post(T event) {
        for (Handler<?> h : handlers) {
            if (!h.type.isInstance(event)) continue;
            if (event instanceof Cancellable c && c.isCancelled() && !h.receiveCancelled) continue;
            try {
                h.invoke(event);
            } catch (Throwable ex) {
                logger.log(Level.WARNING, "[BetterChats] Un manejador de " + event.getClass().getSimpleName()
                        + " (" + h.owner + ") lanzo una excepcion", ex);
            }
        }
        return event;
    }
}
