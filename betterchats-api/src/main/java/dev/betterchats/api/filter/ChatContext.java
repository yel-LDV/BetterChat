package dev.betterchats.api.filter;

import dev.betterchats.api.ApiStatus;
import dev.betterchats.api.PlayerRef;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Contexto inmutable de un mensaje que pasa por los {@link ChatFilter}.
 * Los filtros que modifican el texto devuelven un {@link FilterResult#modify}
 * y el siguiente filtro recibe un contexto nuevo.
 */
@ApiStatus.Stable
public final class ChatContext {

    private final PlayerRef sender;
    private final MessageKind kind;
    private final String teamId;
    private final PlayerRef receiver;
    private final String message;
    private final int messageCount;
    private final long timestamp;
    private final Predicate<String> permissions;

    public ChatContext(PlayerRef sender, MessageKind kind, String teamId, PlayerRef receiver,
                       String message, int messageCount, long timestamp, Predicate<String> permissions) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.teamId = teamId;
        this.receiver = receiver;
        this.message = Objects.requireNonNull(message, "message");
        this.messageCount = Math.max(1, messageCount);
        this.timestamp = timestamp;
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    public PlayerRef getSender() { return sender; }
    public MessageKind getKind() { return kind; }
    /** Id del equipo, o null en un mensaje privado. */
    public String getTeamId() { return teamId; }
    /** Receptor en un mensaje privado, o null en un equipo. */
    public PlayerRef getReceiver() { return receiver; }
    public String getMessage() { return message; }
    /**
     * Cuantos mensajes representa este envio (varias lineas enviadas de una vez
     * cuentan como varios mensajes para el rate limit).
     */
    public int getMessageCount() { return messageCount; }
    public long getTimestamp() { return timestamp; }

    public boolean senderHasPermission(String permission) {
        return permissions.test(permission);
    }

    public ChatContext withMessage(String newMessage) {
        return new ChatContext(sender, kind, teamId, receiver, newMessage, messageCount, timestamp, permissions);
    }
}
