package dev.betterchats.api.spy;

import dev.betterchats.api.ApiStatus;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Mensaje que se reenvia al staff que espia un canal.
 *
 * <pre>{@code
 * api.getSpy().dispatch(SpyMessage.builder("vclans.clan")
 *     .sender(player.getUniqueId(), player.getName())
 *     .target(clan.getName())
 *     .message(texto)
 *     .alreadyReceived(miembrosOnline)
 *     .build());
 * }</pre>
 *
 * <p>El texto del mensaje se trata siempre como texto plano: nunca se
 * interpreta como MiniMessage.</p>
 */
@ApiStatus.Experimental
public final class SpyMessage {

    private final String channel;
    private final UUID senderUuid;
    private final String senderName;
    private final String target;
    private final String message;
    private final Set<UUID> alreadyReceived;
    private final Map<String, String> placeholders;

    private SpyMessage(Builder b) {
        this.channel = b.channel;
        this.senderUuid = b.senderUuid;
        this.senderName = b.senderName;
        this.target = b.target;
        this.message = b.message;
        this.alreadyReceived = Collections.unmodifiableSet(new HashSet<UUID>(b.alreadyReceived));
        this.placeholders = Collections.unmodifiableMap(new LinkedHashMap<String, String>(b.placeholders));
    }

    public static Builder builder(String channel) {
        return new Builder(channel);
    }

    public String getChannel() { return channel; }
    /** Puede ser null (consola u origen no jugador). */
    public UUID getSenderUuid() { return senderUuid; }
    public String getSenderName() { return senderName; }
    /** Destino legible: receptor, nombre del clan, equipo... */
    public String getTarget() { return target; }
    public String getMessage() { return message; }
    /** Jugadores que ya vieron el mensaje original: no se les duplica. */
    public Set<UUID> getAlreadyReceived() { return alreadyReceived; }
    /** Placeholders extra {@code {clave}} -> valor (texto plano). */
    public Map<String, String> getPlaceholders() { return placeholders; }

    public static final class Builder {
        private final String channel;
        private UUID senderUuid;
        private String senderName = "?";
        private String target = "";
        private String message = "";
        private final Set<UUID> alreadyReceived = new HashSet<UUID>();
        private final Map<String, String> placeholders = new LinkedHashMap<String, String>();

        private Builder(String channel) {
            this.channel = Objects.requireNonNull(channel, "channel").toLowerCase(java.util.Locale.ROOT);
        }

        public Builder sender(UUID uuid, String name) {
            this.senderUuid = uuid;
            this.senderName = Objects.requireNonNull(name, "name");
            return this;
        }

        public Builder target(String target) { this.target = Objects.requireNonNull(target, "target"); return this; }
        public Builder message(String message) { this.message = Objects.requireNonNull(message, "message"); return this; }
        public Builder alreadyReceived(Collection<UUID> uuids) { this.alreadyReceived.addAll(uuids); return this; }
        public Builder alreadyReceived(UUID uuid) { this.alreadyReceived.add(uuid); return this; }
        public Builder placeholder(String key, String value) { this.placeholders.put(key, value); return this; }

        public SpyMessage build() {
            return new SpyMessage(this);
        }
    }
}
