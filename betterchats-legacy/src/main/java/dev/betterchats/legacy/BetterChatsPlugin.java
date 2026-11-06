package dev.betterchats.legacy;

import dev.betterchats.api.BetterChatsAPI;
import dev.betterchats.api.event.BetterChatsReloadEvent;
import dev.betterchats.core.BetterChatsCore;
import dev.betterchats.legacy.listener.ChatListener;
import dev.betterchats.legacy.listener.PlayerListener;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Plugin de BetterChats para Spigot/Paper antiguo (1.8.8+). Crea la
 * plataforma, arranca el core y publica la API en el ServicesManager.
 */
public final class BetterChatsPlugin extends JavaPlugin {

    private BukkitAudiences audiences;
    private BukkitPlatform platform;
    private BetterChatsCore core;
    private ChatListener chatListener;
    /** BetterChatsExpansion como Object: su clase solo se carga si PlaceholderAPI esta. */
    private Object papiExpansion;

    @Override
    public void onEnable() {
        audiences = BukkitAudiences.create(this);
        platform = new BukkitPlatform(this, audiences, this::noPermission);
        platform.hookPlaceholderApi();
        core = new BetterChatsCore(platform);
        try {
            core.enable();
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "[BetterChats] No se pudo arrancar", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Jugadores ya conectados (/reload del servidor).
        for (Player p : getServer().getOnlinePlayers()) core.onJoin(platform.bukkitPlayers().add(p));

        chatListener = new ChatListener(this, core, platform.bukkitPlayers()::wrap);
        chatListener.register();
        getServer().getPluginManager().registerEvents(new PlayerListener(core,
                platform.bukkitPlayers()::add, platform.bukkitPlayers()::remove,
                this::hookPlaceholderApi), this);
        hookPlaceholderApi();
        hookPacketEvents();

        // Si cambia chat.listenerPriority en una recarga, se vuelve a registrar.
        core.events().subscribe(this, BetterChatsReloadEvent.class, e -> {
            if (e.getResult().isSuccess()) getServer().getScheduler().runTask(this, chatListener::register);
        });

        // Guardar la cache de nombres junto con el autoguardado.
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> core.data().io().execute(platform.bukkitPlayers()::saveNamesIfDirty), 1200L, 1200L);

        getServer().getServicesManager().register(BetterChatsAPI.class, core, this, ServicePriority.Normal);
    }

    /**
     * PlaceholderAPI: puente para resolver %placeholders% en los formatos y
     * expansion %betterchats_...%. Se reintenta cuando se activa un plugin, por
     * si PlaceholderAPI arranca despues que BetterChats.
     */
    private void hookPlaceholderApi() {
        platform.hookPlaceholderApi();
        if (papiExpansion != null || !getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        try {
            papiExpansion = dev.betterchats.legacy.hook.PapiHooks.register(core, getDescription().getVersion());
            if (papiExpansion != null) getLogger().info("[BetterChats] Placeholders %betterchats_...% registrados.");
        } catch (Throwable t) {
            getLogger().warning("[BetterChats] No se pudo registrar la expansion de PlaceholderAPI: " + t);
        }
    }

    /**
     * Menu de /chatcolor con PacketEvents. Sin PacketEvents, /chatcolor sigue
     * funcionando en modo texto (/chatcolor list, /chatcolor <color>).
     */
    private void hookPacketEvents() {
        if (!getServer().getPluginManager().isPluginEnabled("packetevents")) {
            getLogger().info("[BetterChats] PacketEvents no esta instalado: /chatcolor funcionara sin menu.");
            return;
        }
        try {
            platform.chatColorMenu(dev.betterchats.legacy.menu.PacketChatColorMenu.create(this, core,
                    platform.bukkitPlayers()::wrap));
            getLogger().info("[BetterChats] Menu de /chatcolor activo (PacketEvents).");
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "[BetterChats] No se pudo crear el menu con PacketEvents", t);
        }
    }

    @Override
    public void onDisable() {
        if (platform != null && platform.chatColorMenu() != null) {
            try {
                platform.chatColorMenu().shutdown();
            } catch (Throwable ignored) {
            }
            platform.chatColorMenu(null);
        }
        if (papiExpansion != null) {
            try {
                dev.betterchats.legacy.hook.PapiHooks.unregister(papiExpansion);
            } catch (Throwable ignored) {
            }
            papiExpansion = null;
        }
        getServer().getServicesManager().unregisterAll(this);
        if (core != null) {
            if (platform != null) platform.bukkitPlayers().saveNamesIfDirty();
            core.disable();
        }
        if (audiences != null) {
            audiences.close();
            audiences = null;
        }
    }

    private Component noPermission() {
        return core.config().lang().get("no-permission");
    }

    public BetterChatsAPI api() {
        return core;
    }
}
