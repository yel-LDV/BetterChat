package dev.betterchats.legacy;

import dev.betterchats.core.platform.ChatPlayer;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/** ChatPlayer sobre un Player de Bukkit. Se crea al entrar y se descarta al salir. */
final class BukkitChatPlayer implements ChatPlayer {

    private final Player player;
    private final BukkitAudiences audiences;
    private final UUID uuid;
    private final String name;

    BukkitChatPlayer(Player player, BukkitAudiences audiences) {
        this.player = player;
        this.audiences = audiences;
        this.uuid = player.getUniqueId();
        this.name = player.getName();
    }

    Player bukkit() {
        return player;
    }

    @Override public UUID uuid() { return uuid; }
    @Override public String name() { return name; }

    @Override
    public String displayName() {
        String d = player.getDisplayName();
        return d == null ? name : d;
    }

    @Override
    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public void sendMessage(Component message) {
        audiences.player(player).sendMessage(message);
    }

    @Override
    public boolean isOnline() {
        return player.isOnline();
    }

    @Override
    public PlayerLocation location() {
        Location l = player.getLocation();
        return new PlayerLocation(l.getWorld() == null ? "?" : l.getWorld().getName(), l.getX(), l.getY(), l.getZ());
    }
}
