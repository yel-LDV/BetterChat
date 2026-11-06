package dev.betterchats.core;

import dev.betterchats.api.PlayerRef;
import dev.betterchats.api.RateLimit;
import dev.betterchats.api.filter.ChatContext;
import dev.betterchats.api.filter.FilterResult;
import dev.betterchats.api.filter.MessageKind;
import dev.betterchats.core.config.MainConfig;
import dev.betterchats.core.filter.BuiltinFilters;
import dev.betterchats.core.filter.FilterChain;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiltersTest {

    private final MainConfig.AntiSpam cfg = new MainConfig.AntiSpam(true, 3, 20, "bypass");
    private final BuiltinFilters filters = new BuiltinFilters(() -> cfg,
            team -> Optional.of(new RateLimit(5, 10)), key -> "<red>" + key + " <seconds><max>");
    private final PlayerRef sender = new PlayerRef(UUID.randomUUID(), "Pepe");

    private ChatContext ctx(String msg, long at, int count, boolean bypass) {
        return new ChatContext(sender, MessageKind.TEAM, "default", null, msg, count, at, p -> bypass);
    }

    @Test
    void rateLimitVentanaDeslizante() {
        for (int i = 0; i < 5; i++) {
            assertEquals(FilterResult.Action.ALLOW, filters.rateLimit.filter(ctx("m" + i, 1000 + i, 1, false)).getAction());
        }
        assertEquals(FilterResult.Action.CANCEL, filters.rateLimit.filter(ctx("m6", 1006, 1, false)).getAction());
        // A los 10 s del primero vuelve a dejar pasar uno.
        assertEquals(FilterResult.Action.ALLOW, filters.rateLimit.filter(ctx("m7", 11000, 1, false)).getAction());
    }

    @Test
    void multiMensajeCuentaCadaLinea() {
        assertEquals(FilterResult.Action.ALLOW, filters.rateLimit.filter(ctx("a", 0, 4, false)).getAction());
        assertEquals(FilterResult.Action.CANCEL, filters.rateLimit.filter(ctx("b", 1, 2, false)).getAction());
        assertEquals(FilterResult.Action.ALLOW, filters.rateLimit.filter(ctx("c", 2, 1, false)).getAction());
    }

    @Test
    void duplicadosYBypass() {
        assertEquals(FilterResult.Action.ALLOW, filters.duplicate.filter(ctx("Hola  mundo", 0, 1, false)).getAction());
        assertEquals(FilterResult.Action.CANCEL, filters.duplicate.filter(ctx("hola mundo", 1000, 1, false)).getAction());
        assertEquals(FilterResult.Action.ALLOW, filters.duplicate.filter(ctx("hola mundo", 5000, 1, false)).getAction());
        assertEquals(FilterResult.Action.ALLOW, filters.duplicate.filter(ctx("hola mundo", 5001, 1, true)).getAction());
    }

    @Test
    void longitud() {
        FilterResult r = filters.length.filter(ctx("x".repeat(21), 0, 1, false));
        assertEquals(FilterResult.Action.CANCEL, r.getAction());
        assertTrue(r.getFeedback().contains("20"));
    }

    @Test
    void cadenaRespetaPrioridadYModificaciones() {
        FilterChain chain = new FilterChain(Logger.getLogger("test"));
        chain.register(c -> FilterResult.modify(c.getMessage().replace("tonto", "****")), 10);
        chain.register(c -> c.getMessage().contains("****") ? FilterResult.allow() : FilterResult.cancel("no"), 20);
        FilterChain.Outcome ok = chain.run(ctx("eres tonto", 0, 1, false));
        assertFalse(ok.cancelled());
        assertEquals("eres ****", ok.message());
        FilterChain.Outcome ko = chain.run(ctx("hola", 0, 1, false));
        assertTrue(ko.cancelled());
        assertEquals("no", ko.feedback());
    }
}
