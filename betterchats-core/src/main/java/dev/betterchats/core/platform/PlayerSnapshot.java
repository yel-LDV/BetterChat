package dev.betterchats.core.platform;

import java.util.Map;
import java.util.UUID;

/**
 * Datos del jugador capturados en el hilo principal. El pipeline asincrono
 * solo lee de aqui, nunca del mundo.
 *
 * @param placeholders valores precalculados de PlaceholderAPI ({@code %x%} -> valor)
 */
public record PlayerSnapshot(UUID uuid, String name, String displayName, ChatPlayer.PlayerLocation location,
                             Map<String, String> placeholders, long takenAt) {

    public PlayerSnapshot {
        placeholders = Map.copyOf(placeholders);
    }

    public static PlayerSnapshot minimal(ChatPlayer player) {
        return new PlayerSnapshot(player.uuid(), player.name(), player.displayName(),
                ChatPlayer.PlayerLocation.UNKNOWN, Map.of(), System.currentTimeMillis());
    }
}
