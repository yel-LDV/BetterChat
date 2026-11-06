package dev.betterchats.core.filter;

import dev.betterchats.api.filter.ChatContext;
import dev.betterchats.api.filter.ChatFilter;
import dev.betterchats.api.filter.FilterResult;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Bloquea {@code ${...}} en el chat y en los PMs. Es la forma del exploit
 * Log4Shell ({@code ${jndi:ldap://...}}): en servidores con Log4j antiguo,
 * escribirlo en el chat ejecutaba codigo al registrarse en consola.
 * Defensa en profundidad: se aplica a todos (sin bypass).
 */
public final class LookupFilter implements ChatFilter {

    public static final int PRIORITY = 50;

    private final BooleanSupplier enabled;
    private final Supplier<String> feedback;

    public LookupFilter(BooleanSupplier enabled, Supplier<String> feedback) {
        this.enabled = enabled;
        this.feedback = feedback;
    }

    @Override
    public FilterResult filter(ChatContext ctx) {
        if (enabled.getAsBoolean() && ctx.getMessage().contains("${")) {
            return FilterResult.cancel(feedback.get());
        }
        return FilterResult.allow();
    }

    @Override
    public String getName() {
        return "BetterChats:lookup";
    }
}
