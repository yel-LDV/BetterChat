package dev.betterchats.core.platform;

/** Puente a PlaceholderAPI, o un resolutor vacio si no esta instalado. */
public interface PlaceholderBridge {

    PlaceholderBridge NONE = new PlaceholderBridge() {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String apply(ChatPlayer player, String text) {
            return text;
        }
    };

    boolean isAvailable();

    /** Sustituye los {@code %placeholders%} del texto para ese jugador. */
    String apply(ChatPlayer player, String text);
}
