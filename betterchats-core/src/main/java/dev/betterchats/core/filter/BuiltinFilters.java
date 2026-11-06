package dev.betterchats.core.filter;

import dev.betterchats.api.RateLimit;
import dev.betterchats.api.filter.ChatContext;
import dev.betterchats.api.filter.ChatFilter;
import dev.betterchats.api.filter.FilterResult;
import dev.betterchats.api.filter.MessageKind;
import dev.betterchats.core.config.MainConfig;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Filtros incluidos: longitud (100), duplicados (200) y rate limit (300).
 * Leen la configuracion en cada mensaje via {@link Supplier}, asi una recarga
 * aplica sin volver a registrarlos.
 */
public final class BuiltinFilters {

    public static final int LENGTH_PRIORITY = 100;
    public static final int DUPLICATE_PRIORITY = 200;
    public static final int RATE_PRIORITY = 300;

    private final Supplier<MainConfig.AntiSpam> config;
    private final Function<String, Optional<RateLimit>> teamRateLimit;
    private final Function<String, String> lang;

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final Map<UUID, LastMessage> lastMessages = new ConcurrentHashMap<>();

    private record LastMessage(String normalized, long at) {
    }

    /**
     * @param teamRateLimit rate limit del equipo por id
     * @param lang          mensaje crudo (MiniMessage) por clave
     */
    public BuiltinFilters(Supplier<MainConfig.AntiSpam> config, Function<String, Optional<RateLimit>> teamRateLimit,
                          Function<String, String> lang) {
        this.config = config;
        this.teamRateLimit = teamRateLimit;
        this.lang = lang;
    }

    public final ChatFilter length = new ChatFilter() {
        @Override
        public FilterResult filter(ChatContext ctx) {
            MainConfig.AntiSpam cfg = config.get();
            if (!cfg.enabled() || bypass(ctx, cfg)) return FilterResult.allow();
            if (ctx.getMessage().length() > cfg.maxLength()) {
                return FilterResult.cancel(lang.apply("antispam-length")
                        .replace("<max>", String.valueOf(cfg.maxLength())));
            }
            return FilterResult.allow();
        }

        @Override
        public String getName() {
            return "BetterChats:length";
        }
    };

    public final ChatFilter duplicate = new ChatFilter() {
        @Override
        public FilterResult filter(ChatContext ctx) {
            MainConfig.AntiSpam cfg = config.get();
            if (!cfg.enabled() || cfg.duplicateCooldownSeconds() <= 0 || bypass(ctx, cfg)) return FilterResult.allow();
            String norm = ctx.getMessage().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            long now = ctx.getTimestamp();
            LastMessage prev = lastMessages.put(ctx.getSender().getUuid(), new LastMessage(norm, now));
            if (prev != null && prev.normalized().equals(norm)
                    && now - prev.at() < cfg.duplicateCooldownSeconds() * 1000L) {
                return FilterResult.cancel(lang.apply("antispam-duplicate")
                        .replace("<seconds>", String.valueOf(cfg.duplicateCooldownSeconds())));
            }
            return FilterResult.allow();
        }

        @Override
        public String getName() {
            return "BetterChats:duplicate";
        }
    };

    public final ChatFilter rateLimit = new ChatFilter() {
        @Override
        public FilterResult filter(ChatContext ctx) {
            MainConfig.AntiSpam cfg = config.get();
            if (!cfg.enabled() || bypass(ctx, cfg)) return FilterResult.allow();
            String scope = ctx.getKind() == MessageKind.PRIVATE ? "#pm" : ctx.getTeamId();
            Optional<RateLimit> limit = ctx.getKind() == MessageKind.PRIVATE
                    ? teamRateLimit.apply("#pm") : teamRateLimit.apply(ctx.getTeamId());
            if (limit.isEmpty()) return FilterResult.allow();
            RateLimit rl = limit.get();
            String key = ctx.getSender().getUuid() + "|" + scope;
            long now = ctx.getTimestamp();
            long windowMs = rl.getPerSeconds() * 1000L;
            Deque<Long> q = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (q) {
                while (!q.isEmpty() && now - q.peekFirst() >= windowMs) q.pollFirst();
                if (q.size() + ctx.getMessageCount() > rl.getMessages()) {
                    long waitMs = q.isEmpty() ? windowMs : windowMs - (now - q.peekFirst());
                    return FilterResult.cancel(lang.apply("antispam-rate")
                            .replace("<seconds>", String.valueOf(Math.max(1, (waitMs + 999) / 1000))));
                }
                // Envio de varias lineas: cada una cuenta como un mensaje.
                for (int i = 0; i < ctx.getMessageCount(); i++) q.addLast(now);
            }
            return FilterResult.allow();
        }

        @Override
        public String getName() {
            return "BetterChats:rate-limit";
        }
    };

    private static boolean bypass(ChatContext ctx, MainConfig.AntiSpam cfg) {
        return cfg.bypassPermission() != null && ctx.senderHasPermission(cfg.bypassPermission());
    }

    /** Limpieza al desconectarse (evita fugas de memoria). */
    public void forget(UUID player) {
        lastMessages.remove(player);
        String prefix = player + "|";
        windows.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
