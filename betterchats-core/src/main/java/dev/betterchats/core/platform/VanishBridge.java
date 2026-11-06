package dev.betterchats.core.platform;

/** Detecta jugadores en vanish (metadata "vanished", APIs de plugins de vanish...). */
public interface VanishBridge {

    VanishBridge NONE = new VanishBridge() {
        @Override
        public boolean isVanished(ChatPlayer player) {
            return false;
        }

        @Override
        public boolean canSee(ChatPlayer viewer, ChatPlayer target) {
            return true;
        }
    };

    boolean isVanished(ChatPlayer player);

    /** true si {@code viewer} puede ver a {@code target} (aunque este en vanish). */
    boolean canSee(ChatPlayer viewer, ChatPlayer target);
}
