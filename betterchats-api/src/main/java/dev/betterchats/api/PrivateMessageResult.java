package dev.betterchats.api;

/** Resultado de {@link BetterChatsAPI#sendPrivateMessage}. */
@ApiStatus.Stable
public enum PrivateMessageResult {
    SENT,
    /** Los mensajes privados estan desactivados en private_messages.json. */
    DISABLED,
    /** El receptor no esta conectado (o esta en vanish y el emisor no puede verlo). */
    NOT_FOUND,
    /** Emisor y receptor son el mismo y allowSelfMessage es false. */
    SELF,
    /** El receptor ignora al emisor. */
    IGNORED,
    /** El emisor ignora al receptor. */
    SENDER_IGNORES,
    /** Un filtro (anti-spam, longitud...) bloqueo el mensaje. */
    FILTERED,
    /** Un listener cancelo {@link dev.betterchats.api.event.PrivateMessageEvent}. */
    CANCELLED,
    /** Mensaje vacio. */
    EMPTY
}
