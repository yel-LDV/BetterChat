package dev.betterchats.core.config;

import dev.betterchats.core.format.LegacyColors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mensajes de BetterChats (lang/&lt;idioma&gt;.json). Son plantillas MiniMessage
 * de confianza; los valores que vienen de jugadores se pasan siempre con
 * {@link #unparsed(String, String)}.
 */
public final class Lang {

    private final Map<String, String> messages;
    private final boolean legacyColors;

    /** @param legacyColors convertir {@code &a}/{@code §a} a MiniMessage (chat.legacyColorsInFormats) */
    public Lang(Map<String, String> messages, boolean legacyColors) {
        this.messages = Map.copyOf(messages);
        this.legacyColors = legacyColors;
    }

    private String prepare(String template) {
        return legacyColors ? LegacyColors.toMiniMessage(template) : template;
    }

    public String raw(String key) {
        return messages.getOrDefault(key, "<red>[BetterChats] falta el mensaje '" + key + "'");
    }

    public boolean has(String key) {
        return messages.containsKey(key);
    }

    public Component get(String key, TagResolver... resolvers) {
        return render(raw(key), resolvers);
    }

    /** Renderiza un texto MiniMessage de confianza con {@code <prefix>} disponible. */
    public Component render(String template, TagResolver... resolvers) {
        List<TagResolver> all = new ArrayList<>(resolvers.length + 1);
        all.add(Placeholder.parsed("prefix", prepare(messages.getOrDefault("prefix", ""))));
        all.addAll(List.of(resolvers));
        return MiniMessage.miniMessage().deserialize(prepare(template), TagResolver.resolver(all));
    }

    public static TagResolver unparsed(String key, String value) {
        return Placeholder.unparsed(key, value == null ? "" : value);
    }

    public static TagResolver component(String key, Component value) {
        return Placeholder.component(key, value);
    }
}
