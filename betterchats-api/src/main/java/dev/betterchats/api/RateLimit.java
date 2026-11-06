package dev.betterchats.api;

/** Limite de mensajes por ventana deslizante de tiempo. */
@ApiStatus.Stable
public final class RateLimit {

    private final int messages;
    private final int perSeconds;

    public RateLimit(int messages, int perSeconds) {
        if (messages <= 0 || perSeconds <= 0) {
            throw new IllegalArgumentException("messages y perSeconds deben ser > 0");
        }
        this.messages = messages;
        this.perSeconds = perSeconds;
    }

    public int getMessages() { return messages; }
    public int getPerSeconds() { return perSeconds; }

    @Override
    public String toString() {
        return messages + "/" + perSeconds + "s";
    }
}
