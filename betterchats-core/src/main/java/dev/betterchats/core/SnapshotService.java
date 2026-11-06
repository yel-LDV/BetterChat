package dev.betterchats.core;

import dev.betterchats.core.config.ConfigBundle;
import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.Platform;
import dev.betterchats.core.platform.PlayerSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Snapshots de jugadores (seccion 7.1). Se refrescan en el hilo principal
 * cada {@code snapshot.refreshTicks}; el chat asincrono solo los lee.
 */
public final class SnapshotService {

    private final Platform platform;
    private final Supplier<ConfigBundle> config;
    private final Map<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();

    public SnapshotService(Platform platform, Supplier<ConfigBundle> config) {
        this.platform = platform;
        this.config = config;
    }

    /** Solo hilo principal. */
    public void refresh(ChatPlayer player) {
        Map<String, String> ph = new LinkedHashMap<>();
        if (platform.placeholders().isAvailable()) {
            for (String p : config.get().main().snapshot().placeholders()) {
                ph.put(p, platform.placeholders().apply(player, p));
            }
        }
        snapshots.put(player.uuid(), new PlayerSnapshot(player.uuid(), player.name(), player.displayName(),
                player.location(), ph, System.currentTimeMillis()));
    }

    /** Solo hilo principal. */
    public void refreshAll() {
        for (ChatPlayer p : platform.players().online()) refresh(p);
    }

    public PlayerSnapshot get(ChatPlayer player) {
        PlayerSnapshot s = snapshots.get(player.uuid());
        return s != null ? s : PlayerSnapshot.minimal(player);
    }

    public void forget(UUID player) {
        snapshots.remove(player);
    }
}
