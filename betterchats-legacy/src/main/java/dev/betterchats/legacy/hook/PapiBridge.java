package dev.betterchats.legacy.hook;

import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.PlaceholderBridge;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI. Esta clase solo se carga si PlaceholderAPI esta instalado
 * (referencia sus clases).
 */
public final class PapiBridge implements PlaceholderBridge {

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    @Override
    public String apply(ChatPlayer player, String text) {
        Player p = Bukkit.getPlayer(player.uuid());
        if (p == null) return text;
        return PlaceholderAPI.setPlaceholders(p, text);
    }
}
