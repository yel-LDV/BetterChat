package dev.betterchats.legacy.menu;

import org.bukkit.entity.Player;

/**
 * Menu de /chatcolor visto desde el plugin, sin tipos de PacketEvents (asi
 * BetterChats arranca aunque PacketEvents no este instalado).
 */
public interface ChatColorMenu {

    /** Hilo principal. */
    void open(Player player);

    /** Cierra los menus abiertos y quita el listener de paquetes. */
    void shutdown();
}
