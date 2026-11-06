package dev.betterchats.legacy.menu;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.adventure.AdventureSerializer;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import dev.betterchats.api.SoundSettings;
import dev.betterchats.core.BetterChatsCore;
import dev.betterchats.core.ChatStyleService;
import dev.betterchats.core.command.ChatColorFeedback;
import dev.betterchats.core.config.ChatColorsConfig;
import dev.betterchats.core.format.FormatPipeline;
import dev.betterchats.core.platform.ChatPlayer;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Menu de colores de chat hecho solo con paquetes (PacketEvents): el servidor
 * no abre ningun inventario real, asi que no hay items que robar ni
 * conflictos con otros plugins de inventarios.
 *
 * <ul>
 *   <li>OPEN_WINDOW + WINDOW_ITEMS dibujan el cofre.</li>
 *   <li>Los CLICK_WINDOW de nuestra ventana se cancelan y se procesan en el
 *       hilo principal; despues se redibuja y se resincroniza el inventario del
 *       jugador (el cliente predice el movimiento del item).</li>
 *   <li>CLOSE_WINDOW cierra la sesion.</li>
 * </ul>
 *
 * <p>PacketEvents trae Adventure sin reubicar y BetterChats el suyo reubicado:
 * el titulo (un Component de PacketEvents) se crea y se pasa por reflexion para
 * que ningun tipo de Adventure cruce entre los dos.</p>
 */
public final class PacketChatColorMenu implements PacketListener, ChatColorMenu {

    /** Fuera del rango 1..100 que usa el servidor 1.8 para sus ventanas. */
    static final int WINDOW_ID = 101;
    private static final String RESET = "#reset";

    private final Plugin plugin;
    private final BetterChatsCore core;
    private final Function<Player, ChatPlayer> wrap;
    /** Minimo entre dos clics procesados del mismo jugador. */
    private static final long CLICK_COOLDOWN_MS = 150;

    private final Map<UUID, Map<Integer, String>> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final Set<String> warnedMaterials = ConcurrentHashMap.newKeySet();
    private final Method legacyToTitle;
    private final Constructor<WrapperPlayServerOpenWindow> openWindow;
    private PacketListenerCommon registration;

    private PacketChatColorMenu(Plugin plugin, BetterChatsCore core, Function<Player, ChatPlayer> wrap)
            throws ReflectiveOperationException {
        this.plugin = plugin;
        this.core = core;
        this.wrap = wrap;
        this.legacyToTitle = AdventureSerializer.class.getMethod("fromLegacyFormat", String.class);
        this.openWindow = WrapperPlayServerOpenWindow.class.getConstructor(int.class, String.class,
                legacyToTitle.getReturnType(), int.class, int.class);
    }

    public static ChatColorMenu create(Plugin plugin, BetterChatsCore core, Function<Player, ChatPlayer> wrap)
            throws ReflectiveOperationException {
        PacketChatColorMenu menu = new PacketChatColorMenu(plugin, core, wrap);
        menu.registration = PacketEvents.getAPI().getEventManager()
                .registerListener(menu, PacketListenerPriority.NORMAL);
        return menu;
    }

    // ------------------------------------------------------------------ abrir

    @Override
    public void open(Player player) {
        ChatColorsConfig cfg = core.config().chatColors();
        int size = cfg.rows() * 9;
        String title = ChatColor.translateAlternateColorCodes('&', cfg.title());
        if (title.length() > 32) title = title.substring(0, 32); // limite de 1.8
        try {
            Object component = legacyToTitle.invoke(null, title);
            send(player, openWindow.newInstance(WINDOW_ID, "minecraft:chest", component, size, 0));
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.SEVERE, "[BetterChats] No se pudo abrir el menu de colores", ex);
            return;
        }
        Map<Integer, String> slots = new HashMap<>();
        sessions.put(player.getUniqueId(), slots);
        redraw(player, slots);
    }

    /** Dibuja todos los iconos con su estado (seleccionado / disponible / bloqueado). */
    private void redraw(Player player, Map<Integer, String> slots) {
        ChatColorsConfig cfg = core.config().chatColors();
        ChatPlayer cp = wrap.apply(player);
        ChatStyleService style = core.chatStyle();
        int size = cfg.rows() * 9;

        List<com.github.retrooper.packetevents.protocol.item.ItemStack> items = new ArrayList<>(size);
        com.github.retrooper.packetevents.protocol.item.ItemStack filler = cfg.filler() == null
                ? com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY
                : icon(cfg.filler(), null, false);
        for (int i = 0; i < size; i++) items.add(filler);
        slots.clear();

        for (ChatColorsConfig.ColorOption c : cfg.colors()) {
            place(items, slots, c.item(), c.id(), status(cfg, style, cp, c.id(), c.permission()),
                    style.isSelected(cp, c.id()));
        }
        for (ChatColorsConfig.FormatOption f : cfg.formats()) {
            place(items, slots, f.item(), f.id(), status(cfg, style, cp, f.id(), f.permission()),
                    style.isSelected(cp, f.id()));
        }
        place(items, slots, cfg.reset(), RESET, null, false);

        send(player, new WrapperPlayServerWindowItems(WINDOW_ID, 0, items,
                com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY));
    }

    private void place(List<com.github.retrooper.packetevents.protocol.item.ItemStack> items,
                       Map<Integer, String> slots, ChatColorsConfig.Item item, String id, String status,
                       boolean selected) {
        if (item.slot() < 0 || item.slot() >= items.size()) return;
        items.set(item.slot(), icon(item, status, selected));
        slots.put(item.slot(), id);
    }

    private static String status(ChatColorsConfig cfg, ChatStyleService style, ChatPlayer cp, String id,
                                 String permission) {
        if (!style.canUse(cp, permission)) return cfg.statusLocked();
        return style.isSelected(cp, id) ? cfg.statusSelected() : cfg.statusAvailable();
    }

    private com.github.retrooper.packetevents.protocol.item.ItemStack icon(ChatColorsConfig.Item item, String status,
                                                                           boolean glow) {
        Material material = Material.matchMaterial(item.material());
        if (material == null) {
            if (warnedMaterials.add(item.material())) {
                plugin.getLogger().warning("[BetterChats] chat_colors.json: material '" + item.material()
                        + "' no existe en este servidor; se usa STONE");
            }
            material = Material.STONE;
        }
        ItemStack stack = new ItemStack(material, 1, (short) item.data());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(item.name()));
            List<String> lore = new ArrayList<>();
            for (String line : item.lore()) lore.add(color(line));
            if (status != null) {
                if (!lore.isEmpty()) lore.add("");
                lore.add(color(status));
            }
            meta.setLore(lore);
            if (glow) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return SpigotConversionUtil.fromBukkitItemStack(stack);
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static void send(Player player, PacketWrapper<?> packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    // ---------------------------------------------------------------- paquetes

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);
            if (click.getWindowId() != WINDOW_ID) return;
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (player == null) return;
            UUID id = player.getUniqueId();
            // Anti-flood: sin menu abierto no se hace nada; con menu, como mucho
            // una tarea en cola por jugador y un clic procesado cada CLICK_COOLDOWN_MS
            // (un cliente modificado puede mandar miles de clics por segundo).
            if (!sessions.containsKey(id) || !pending.add(id)) return;
            long now = System.currentTimeMillis();
            Long last = lastClick.put(id, now);
            int slot = last != null && now - last < CLICK_COOLDOWN_MS ? -1 : click.getSlot();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    handleClick(player, slot);
                } finally {
                    pending.remove(id);
                }
            });
        } else if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            WrapperPlayClientCloseWindow close = new WrapperPlayClientCloseWindow(event);
            if (close.getWindowId() != WINDOW_ID) return;
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (player != null) forget(player.getUniqueId());
        }
    }

    private void forget(UUID id) {
        sessions.remove(id);
        lastClick.remove(id);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        // Si el servidor abre otra ventana, nuestro menu deja de estar en pantalla.
        if (event.getPacketType() == PacketType.Play.Server.OPEN_WINDOW) {
            Player player = event.getPlayer();
            if (player == null || !sessions.containsKey(player.getUniqueId())) return;
            WrapperPlayServerOpenWindow open = new WrapperPlayServerOpenWindow(event);
            if (open.getContainerId() != WINDOW_ID) forget(player.getUniqueId());
        }
    }

    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        UUID id = event.getUser().getUUID();
        if (id != null) {
            forget(id);
            pending.remove(id);
        }
    }

    /** Hilo principal. */
    private void handleClick(Player player, int slot) {
        Map<Integer, String> slots = sessions.get(player.getUniqueId());
        if (slots == null || !player.isOnline()) return;
        String id = slots.get(slot);
        if (id != null) {
            ChatPlayer cp = wrap.apply(player);
            var lang = core.config().lang();
            if (id.equals(RESET)) {
                core.chatStyle().reset(cp);
                cp.sendMessage(lang.get("chatcolor-reset"));
            } else {
                ChatStyleService.Result r = core.chatStyle().toggle(cp, id);
                cp.sendMessage(ChatColorFeedback.of(lang, r, FormatPipeline.legacyToComponent(nameOf(id))));
            }
            core.platform().sounds().play(cp, new SoundSettings(true, "ui.button.click", 0.6f, 1.2f));
        }
        // Deshacer la prediccion del cliente: redibujar, vaciar el cursor y
        // reenviar su inventario real (por clicks con shift o teclas 1-9).
        redraw(player, slots);
        send(player, new WrapperPlayServerSetSlot(-1, 0, -1,
                com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY));
        player.updateInventory();
    }

    private String nameOf(String id) {
        ChatColorsConfig cfg = core.config().chatColors();
        return cfg.color(id).map(c -> c.item().name())
                .or(() -> cfg.format(id).map(f -> f.item().name()))
                .orElse(id);
    }

    @Override
    public void shutdown() {
        for (UUID id : sessions.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) send(p, new WrapperPlayServerCloseWindow(WINDOW_ID));
        }
        sessions.clear();
        lastClick.clear();
        if (registration != null) PacketEvents.getAPI().getEventManager().unregisterListener(registration);
    }
}
