package dev.betterchats.core.config;

import java.util.List;
import java.util.Map;

/**
 * spy.json: modo spy unificado. Cada canal (propio o de otro plugin) puede
 * tener aqui su formato, permiso y si esta activo.
 */
public record SpyConfig(boolean enabled, List<String> commandAliases, boolean logToConsole,
                        boolean restoreOnJoin, Map<String, ChannelOverride> channels) {

    /** Campos null = usar lo que registro el canal. */
    public record ChannelOverride(Boolean enabled, String permission, String format) {
    }
}
