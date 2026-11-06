package dev.betterchats.core.filter;

import dev.betterchats.api.filter.ChatContext;
import dev.betterchats.api.filter.ChatFilter;
import dev.betterchats.api.filter.FilterResult;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Filtros registrados, ejecutados por prioridad ascendente. */
public final class FilterChain {

    /** Resultado final: cancelado (con aviso opcional) o mensaje (posiblemente modificado). */
    public record Outcome(boolean cancelled, String message, String feedback) {
    }

    private record Registered(ChatFilter filter, int priority, long order) {
    }

    private final List<Registered> filters = new CopyOnWriteArrayList<>();
    private final Logger logger;
    private long counter;

    public FilterChain(Logger logger) {
        this.logger = logger;
    }

    public synchronized void register(ChatFilter filter, int priority) {
        unregister(filter);
        filters.add(new Registered(filter, priority, counter++));
        filters.sort(Comparator.comparingInt(Registered::priority).thenComparingLong(Registered::order));
    }

    public synchronized void unregister(ChatFilter filter) {
        filters.removeIf(r -> r.filter() == filter);
    }

    public Outcome run(ChatContext context) {
        ChatContext ctx = context;
        for (Registered r : filters) {
            FilterResult res;
            try {
                res = r.filter().filter(ctx);
            } catch (Throwable ex) {
                logger.log(Level.WARNING, "[BetterChats] El filtro " + r.filter().getName() + " lanzo una excepcion", ex);
                continue;
            }
            if (res == null) continue;
            switch (res.getAction()) {
                case CANCEL -> {
                    return new Outcome(true, ctx.getMessage(), res.getFeedback());
                }
                case MODIFY -> ctx = ctx.withMessage(res.getMessage());
                case ALLOW -> {
                }
            }
        }
        return new Outcome(false, ctx.getMessage(), null);
    }
}
