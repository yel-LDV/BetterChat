package dev.betterchats.legacy;

import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.VanishBridge;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

/**
 * Vanish por la convencion de metadata {@code "vanished"} (SuperVanish,
 * PremiumVanish, Essentials, VanishNoPacket... y VelezCore desde su soporte
 * de BetterChats) mas {@code canSee} de Bukkit.
 */
final class BukkitVanishBridge implements VanishBridge {

    @Override
    public boolean isVanished(ChatPlayer player) {
        if (!(player instanceof BukkitChatPlayer bcp)) return false;
        for (MetadataValue v : bcp.bukkit().getMetadata("vanished")) {
            if (v.asBoolean()) return true;
        }
        return false;
    }

    @Override
    public boolean canSee(ChatPlayer viewer, ChatPlayer target) {
        if (!(viewer instanceof BukkitChatPlayer v) || !(target instanceof BukkitChatPlayer t)) return true;
        Player tp = t.bukkit();
        return v.bukkit().canSee(tp);
    }
}
