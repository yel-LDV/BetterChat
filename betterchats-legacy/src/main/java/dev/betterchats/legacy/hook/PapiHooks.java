package dev.betterchats.legacy.hook;

import dev.betterchats.core.BetterChatsCore;

/** Registro de la expansion aislado: solo se carga con PlaceholderAPI presente. */
public final class PapiHooks {

    private PapiHooks() {
    }

    /** @return la expansion registrada, o null si PlaceholderAPI la rechazo. */
    public static Object register(BetterChatsCore core, String version) {
        BetterChatsExpansion expansion = new BetterChatsExpansion(core, version);
        return expansion.register() ? expansion : null;
    }

    public static void unregister(Object expansion) {
        if (expansion instanceof BetterChatsExpansion e) e.unregister();
    }
}
