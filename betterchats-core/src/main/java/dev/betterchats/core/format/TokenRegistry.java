package dev.betterchats.core.format;

import dev.betterchats.api.PlayerRef;
import dev.betterchats.api.text.MessageToken;
import dev.betterchats.api.text.RichText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Tokens registrados por otros plugins ({@code [item]}, etc.). */
public final class TokenRegistry {

    public record Applied(Component body, boolean replaced) {
    }

    private record Entry(MessageToken token, Pattern pattern) {
    }

    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();
    private final Logger logger;

    public TokenRegistry(Logger logger) {
        this.logger = logger;
    }

    public void register(MessageToken token) {
        int flags = token.isCaseInsensitive() ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
        tokens.put(token.getId().toLowerCase(java.util.Locale.ROOT),
                new Entry(token, Pattern.compile(Pattern.quote(token.getTrigger()), flags)));
    }

    public void unregister(String id) {
        tokens.remove(id.toLowerCase(java.util.Locale.ROOT));
    }

    public boolean isEmpty() {
        return tokens.isEmpty();
    }

    /**
     * Sustituye los tokens que aparezcan en {@code raw} dentro del body ya
     * sanitizado. Un token que falla no rompe el mensaje: se deja el texto.
     */
    public Applied apply(Component body, String raw, PlayerRef sender, Predicate<String> hasPermission) {
        if (tokens.isEmpty()) return new Applied(body, false);
        Component out = body;
        boolean replaced = false;
        for (Entry e : tokens.values()) {
            MessageToken t = e.token();
            if (!e.pattern().matcher(raw).find()) continue;
            if (t.getPermission() != null && !hasPermission.test(t.getPermission())) continue;
            Component rendered;
            try {
                RichText rich = t.render(sender);
                if (rich == null) continue;
                rendered = RichTexts.toComponent(rich);
            } catch (Throwable ex) {
                logger.log(Level.WARNING, "[BetterChats] El token '" + t.getId() + "' fallo al renderizarse", ex);
                continue;
            }
            out = out.replaceText(TextReplacementConfig.builder()
                    .match(e.pattern())
                    .replacement(rendered)
                    .times(Math.max(1, t.getMaxPerMessage()))
                    .build());
            replaced = true;
        }
        return new Applied(out, replaced);
    }
}
