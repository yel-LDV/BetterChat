package dev.betterchats.core.spy;

import dev.betterchats.api.event.EventBus;
import dev.betterchats.api.event.SpyMessageEvent;
import dev.betterchats.api.spy.SpyChannel;
import dev.betterchats.api.spy.SpyMessage;
import dev.betterchats.api.spy.SpyService;
import dev.betterchats.core.config.ConfigBundle;
import dev.betterchats.core.config.SpyConfig;
import dev.betterchats.core.data.PlayerData;
import dev.betterchats.core.data.PlayerDataManager;
import dev.betterchats.core.format.FormatPipeline;
import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.Platform;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Modo spy unificado. Los canales los registran BetterChats ({@code pm},
 * {@code team}) y otros plugins ({@code velezcore.msg}, {@code vclans.clan},
 * {@code vclans.ally}...). {@code spy.json} puede cambiar formato, permiso o
 * desactivar cualquiera.
 */
public final class SpyServiceImpl implements SpyService {

    /** Marca guardada en PlayerData: espia todos los canales permitidos (tambien los futuros). */
    public static final String ALL = "*";
    /** Permiso que concede todos los canales. */
    public static final String ALL_PERMISSION = "betterchats.spy.all";

    public static final String PM = "pm";
    public static final String TEAM = "team";

    private final Platform platform;
    private final PlayerDataManager data;
    private final EventBus events;
    private final FormatPipeline pipeline;
    private final Supplier<ConfigBundle> config;
    private final Map<String, SpyChannel> channels = new ConcurrentHashMap<>();

    public SpyServiceImpl(Platform platform, PlayerDataManager data, EventBus events, FormatPipeline pipeline,
                          Supplier<ConfigBundle> config) {
        this.platform = platform;
        this.data = data;
        this.events = events;
        this.pipeline = pipeline;
        this.config = config;
    }

    /** Canales propios; se llama al arrancar y en cada recarga. */
    public void registerBuiltins() {
        var pm = config.get().privateMessages().spy();
        registerChannel(new SpyChannel(PM, "Mensajes privados", pm.permission(), pm.format()));
        registerChannel(new SpyChannel(TEAM, "Chats privados", "betterchats.spy.team",
                "<dark_gray>[Spy] <gray>[{target}<gray>] {sender}: <white>{message}"));
    }

    @Override
    public void registerChannel(SpyChannel channel) {
        channels.put(channel.getId(), channel);
    }

    @Override
    public void unregisterChannel(String channelId) {
        String id = channelId.toLowerCase(Locale.ROOT);
        if (!id.equals(PM) && !id.equals(TEAM)) channels.remove(id);
    }

    @Override
    public Optional<SpyChannel> getChannel(String channelId) {
        return Optional.ofNullable(channels.get(channelId.toLowerCase(Locale.ROOT)));
    }

    @Override
    public Collection<SpyChannel> getChannels() {
        List<SpyChannel> list = new ArrayList<>(channels.values());
        list.sort((a, b) -> a.getId().compareTo(b.getId()));
        return list;
    }

    /** Canal activo segun spy.json / private_messages.json. */
    public boolean isChannelEnabled(SpyChannel ch) {
        ConfigBundle cfg = config.get();
        if (!cfg.spy().enabled()) return false;
        SpyConfig.ChannelOverride o = cfg.spy().channels().get(ch.getId());
        if (o != null && o.enabled() != null) return o.enabled();
        if (ch.getId().equals(PM)) return cfg.privateMessages().spy().enabled();
        return true;
    }

    public String permissionOf(SpyChannel ch) {
        SpyConfig.ChannelOverride o = config.get().spy().channels().get(ch.getId());
        return o != null && o.permission() != null ? o.permission() : ch.getPermission();
    }

    private String formatOf(SpyChannel ch) {
        SpyConfig.ChannelOverride o = config.get().spy().channels().get(ch.getId());
        return o != null && o.format() != null ? o.format() : ch.getDefaultFormat();
    }

    public boolean canSpy(ChatPlayer player, SpyChannel ch) {
        return player.hasPermission(ALL_PERMISSION) || player.hasPermission(permissionOf(ch));
    }

    @Override
    public void dispatch(SpyMessage message) {
        SpyChannel ch = channels.get(message.getChannel());
        if (ch == null || !isChannelEnabled(ch)) return;

        Set<UUID> spies = new HashSet<>();
        for (ChatPlayer p : platform.players().online()) {
            UUID id = p.uuid();
            if (message.getAlreadyReceived().contains(id) || id.equals(message.getSenderUuid())) continue;
            if (!isSpying(id, ch.getId()) || !canSpy(p, ch)) continue;
            spies.add(id);
        }
        boolean log = config.get().spy().logToConsole();
        if (spies.isEmpty() && !log) return;

        SpyMessageEvent event = events.post(new SpyMessageEvent(message, spies));
        if (event.isCancelled()) return;

        Map<String, String> text = new HashMap<>(message.getPlaceholders());
        text.put("sender", message.getSenderName());
        text.put("target", message.getTarget());
        text.put("receiver", message.getTarget());
        text.put("message", message.getMessage());
        text.put("channel", ch.getDisplayName());
        // PlaceholderAPI del emisor (si esta conectado) en el formato del spy.
        ChatPlayer subject = message.getSenderUuid() == null ? null
                : platform.players().get(message.getSenderUuid()).orElse(null);
        Component line = dev.betterchats.core.util.PacketSafety.limit(pipeline.render(formatOf(ch), text, Map.of(),
                subject, platform.placeholders(), null, config.get().main().chat().legacyColorsInFormats()));

        for (UUID id : event.getSpies()) {
            platform.players().get(id).ifPresent(p -> p.sendMessage(line));
        }
        if (log) platform.sendConsole(line);
    }

    @Override
    public boolean isSpying(UUID player, String channelId) {
        PlayerData d = data.cached(player);
        if (d == null) d = data.get(player);
        return d.isSpying(ALL) || d.isSpying(channelId.toLowerCase(Locale.ROOT));
    }

    @Override
    public void setSpying(UUID player, String channelId, boolean spying) {
        PlayerData d = data.get(player);
        String id = channelId.toLowerCase(Locale.ROOT);
        if (id.equals(ALL)) {
            for (String c : d.spyChannels()) d.setSpying(c, false);
            d.setSpying(ALL, spying);
            return;
        }
        if (!spying && d.isSpying(ALL)) {
            // Pasar de "todos" a "todos menos este": se expanden los canales actuales.
            d.setSpying(ALL, false);
            for (SpyChannel c : channels.values()) {
                if (!c.getId().equals(id)) d.setSpying(c.getId(), true);
            }
            return;
        }
        d.setSpying(id, spying);
    }

    /** Canales que este jugador esta espiando de verdad (con permiso y activos). */
    public List<SpyChannel> activeFor(ChatPlayer player) {
        List<SpyChannel> out = new ArrayList<>();
        for (SpyChannel ch : getChannels()) {
            if (isChannelEnabled(ch) && canSpy(player, ch) && isSpying(player.uuid(), ch.getId())) out.add(ch);
        }
        return out;
    }

    /** Al unirse, si restoreOnJoin es false se apaga el spy. */
    public void onJoin(ChatPlayer player) {
        if (config.get().spy().restoreOnJoin()) return;
        PlayerData d = data.cached(player.uuid());
        if (d == null) return;
        for (String c : d.spyChannels()) d.setSpying(c, false);
    }
}
