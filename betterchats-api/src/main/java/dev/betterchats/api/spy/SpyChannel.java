package dev.betterchats.api.spy;

import dev.betterchats.api.ApiStatus;

import java.util.Objects;

/**
 * Canal que el staff puede espiar con {@code /spy}. BetterChats registra
 * {@code pm} (sus mensajes privados) y {@code team} (equipos privados); otros
 * plugins registran los suyos (p. ej. {@code velezcore.msg},
 * {@code vclans.clan}, {@code vclans.ally}).
 *
 * <p>El formato es MiniMessage con los placeholders {@code {sender}},
 * {@code {target}}, {@code {message}} y {@code {channel}}, mas los extra que
 * aporte cada {@link SpyMessage}. Puede sobrescribirse en {@code spy.json}.</p>
 */
@ApiStatus.Experimental
public final class SpyChannel {

    private final String id;
    private final String displayName;
    private final String permission;
    private final String defaultFormat;

    /**
     * @param id            identificador en minusculas, p. ej. {@code vclans.clan}
     * @param displayName   nombre corto para /spy list
     * @param permission    permiso para espiarlo; null = {@code betterchats.spy.<id>}
     * @param defaultFormat formato MiniMessage por defecto
     */
    public SpyChannel(String id, String displayName, String permission, String defaultFormat) {
        this.id = Objects.requireNonNull(id, "id").toLowerCase(java.util.Locale.ROOT);
        this.displayName = displayName == null ? id : displayName;
        this.permission = permission == null ? "betterchats.spy." + this.id : permission;
        this.defaultFormat = Objects.requireNonNull(defaultFormat, "defaultFormat");
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getPermission() { return permission; }
    public String getDefaultFormat() { return defaultFormat; }
}
