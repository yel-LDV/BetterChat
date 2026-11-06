package dev.betterchats.core.platform;

import net.kyori.adventure.text.Component;

import java.util.Optional;

/** Quien ejecuta un comando (jugador o consola). */
public interface CommandSource {

    String name();

    boolean hasPermission(String permission);

    void sendMessage(Component message);

    /** El jugador, si el origen es un jugador. */
    Optional<ChatPlayer> player();
}
