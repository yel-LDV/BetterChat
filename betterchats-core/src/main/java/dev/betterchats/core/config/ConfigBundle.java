package dev.betterchats.core.config;

import dev.betterchats.api.TeamDefinition;

import java.util.List;

/** Toda la configuracion cargada y validada de una vez (recarga transaccional). */
public record ConfigBundle(MainConfig main, PrivateMessagesConfig privateMessages, SpyConfig spy,
                           ChatColorsConfig chatColors, List<TeamDefinition> teams, Lang lang) {

    public ConfigBundle {
        teams = List.copyOf(teams);
    }
}
