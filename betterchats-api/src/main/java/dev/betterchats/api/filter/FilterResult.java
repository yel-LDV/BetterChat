package dev.betterchats.api.filter;

import dev.betterchats.api.ApiStatus;

/** Resultado de un {@link ChatFilter}. */
@ApiStatus.Stable
public final class FilterResult {

    public enum Action { ALLOW, MODIFY, CANCEL }

    private static final FilterResult ALLOW = new FilterResult(Action.ALLOW, null, null);
    private static final FilterResult CANCEL_SILENT = new FilterResult(Action.CANCEL, null, null);

    private final Action action;
    private final String message;
    private final String feedback;

    private FilterResult(Action action, String message, String feedback) {
        this.action = action;
        this.message = message;
        this.feedback = feedback;
    }

    public static FilterResult allow() {
        return ALLOW;
    }

    /** Sustituye el texto del mensaje y sigue con los demas filtros. */
    public static FilterResult modify(String newMessage) {
        if (newMessage == null) throw new IllegalArgumentException("newMessage");
        return new FilterResult(Action.MODIFY, newMessage, null);
    }

    /** Cancela sin avisar al jugador. */
    public static FilterResult cancel() {
        return CANCEL_SILENT;
    }

    /** Cancela y avisa al jugador con un texto MiniMessage. */
    public static FilterResult cancel(String feedbackMiniMessage) {
        return new FilterResult(Action.CANCEL, null, feedbackMiniMessage);
    }

    public Action getAction() { return action; }
    /** Texto nuevo si la accion es MODIFY. */
    public String getMessage() { return message; }
    /** Aviso al jugador si la accion es CANCEL (puede ser null). */
    public String getFeedback() { return feedback; }
}
