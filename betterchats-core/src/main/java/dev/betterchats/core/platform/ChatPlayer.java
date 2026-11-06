package dev.betterchats.core.platform;

import dev.betterchats.api.PlayerRef;
import net.kyori.adventure.text.Component;

import java.util.UUID;

/**
 * Jugador visto desde el core. Lo implementa cada adaptador.
 *
 * <p>{@link #hasPermission}, {@link #sendMessage} y los getters de identidad
 * son seguros desde cualquier hilo. {@link #location()} solo en el hilo
 * principal (lo usa {@code SnapshotService}).</p>
 */
public interface ChatPlayer {

    UUID uuid();

    String name();

    /** Nombre visible (nick) con codigos legacy. */
    String displayName();

    boolean hasPermission(String permission);

    void sendMessage(Component message);

    boolean isOnline();

    /** Solo hilo principal. */
    PlayerLocation location();

    default PlayerRef ref() {
        return new PlayerRef(uuid(), name());
    }

    record PlayerLocation(String world, double x, double y, double z) {
        public static final PlayerLocation UNKNOWN = new PlayerLocation("?", 0, 0, 0);
    }
}
