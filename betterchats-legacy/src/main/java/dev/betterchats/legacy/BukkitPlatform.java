package dev.betterchats.legacy;

import dev.betterchats.core.platform.CommandRegistrar;
import dev.betterchats.core.platform.Platform;
import dev.betterchats.core.platform.PlaceholderBridge;
import dev.betterchats.core.platform.PlayerProvider;
import dev.betterchats.core.platform.Scheduler;
import dev.betterchats.core.platform.SoundPlayer;
import dev.betterchats.core.platform.VanishBridge;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Platform de Bukkit/Spigot (1.8.8 en adelante). */
final class BukkitPlatform implements Platform {

    private final JavaPlugin plugin;
    private final BukkitAudiences audiences;
    private final BukkitPlayerProvider players;
    private final BukkitSchedulerAdapter scheduler;
    private final BukkitSoundPlayer sounds = new BukkitSoundPlayer();
    private final BukkitVanishBridge vanish = new BukkitVanishBridge();
    private final BukkitCommandRegistrar commands;
    private volatile PlaceholderBridge placeholders = PlaceholderBridge.NONE;
    private volatile dev.betterchats.legacy.menu.ChatColorMenu menu;

    BukkitPlatform(JavaPlugin plugin, BukkitAudiences audiences, Supplier<Component> noPermission) {
        this.plugin = plugin;
        this.audiences = audiences;
        this.players = new BukkitPlayerProvider(audiences, plugin.getDataFolder().toPath(), plugin.getLogger());
        this.scheduler = new BukkitSchedulerAdapter(plugin);
        this.commands = new BukkitCommandRegistrar(audiences, players, noPermission, plugin.getLogger());
    }

    /** Se engancha a PlaceholderAPI si esta (puede activarse despues que nosotros). */
    void hookPlaceholderApi() {
        if (placeholders.isAvailable()) return;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholders = new dev.betterchats.legacy.hook.PapiBridge();
            plugin.getLogger().info("[BetterChats] Enganchado a PlaceholderAPI.");
        }
    }

    BukkitPlayerProvider bukkitPlayers() {
        return players;
    }

    @Override
    public String platformName() {
        return Bukkit.getName() + " " + Bukkit.getBukkitVersion();
    }

    @Override public Logger logger() { return plugin.getLogger(); }
    @Override public Path dataFolder() { return plugin.getDataFolder().toPath(); }
    @Override public InputStream resource(String path) { return plugin.getResource(path); }
    @Override public PlayerProvider players() { return players; }
    @Override public Scheduler scheduler() { return scheduler; }
    @Override public SoundPlayer sounds() { return sounds; }
    @Override public PlaceholderBridge placeholders() { return placeholders; }
    @Override public VanishBridge vanish() { return vanish; }
    @Override public CommandRegistrar commands() { return commands; }

    @Override
    public void sendConsole(Component message) {
        audiences.console().sendMessage(message);
    }

    /** Menu de /chatcolor (null si PacketEvents no esta instalado). */
    void chatColorMenu(dev.betterchats.legacy.menu.ChatColorMenu menu) {
        this.menu = menu;
    }

    dev.betterchats.legacy.menu.ChatColorMenu chatColorMenu() {
        return menu;
    }

    @Override
    public boolean openChatColorMenu(dev.betterchats.core.platform.ChatPlayer player) {
        dev.betterchats.legacy.menu.ChatColorMenu m = menu;
        if (m == null || !(player instanceof BukkitChatPlayer bcp)) return false;
        m.open(bcp.bukkit());
        return true;
    }
}
