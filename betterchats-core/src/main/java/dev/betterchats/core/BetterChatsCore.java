package dev.betterchats.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.betterchats.api.BetterChatsAPI;
import dev.betterchats.api.ChatTeam;
import dev.betterchats.api.PrivateMessageResult;
import dev.betterchats.api.ReloadResult;
import dev.betterchats.api.TeamDefinition;
import dev.betterchats.api.event.BetterChatsReloadEvent;
import dev.betterchats.api.event.ChatActiveTeamChangeEvent;
import dev.betterchats.api.event.ChatTeamCreateEvent;
import dev.betterchats.api.event.ChatTeamDeleteEvent;
import dev.betterchats.api.event.ChatTeamJoinEvent;
import dev.betterchats.api.event.ChatTeamLeaveEvent;
import dev.betterchats.api.event.EventBus;
import dev.betterchats.api.event.MembershipCause;
import dev.betterchats.api.filter.ChatFilter;
import dev.betterchats.api.spy.SpyService;
import dev.betterchats.api.text.MessageToken;
import dev.betterchats.core.command.CommandService;
import dev.betterchats.core.config.ConfigBundle;
import dev.betterchats.core.config.ConfigLoader;
import dev.betterchats.core.data.JsonStorageProvider;
import dev.betterchats.core.data.PlayerData;
import dev.betterchats.core.data.PlayerDataManager;
import dev.betterchats.core.event.EventBusImpl;
import dev.betterchats.core.filter.BuiltinFilters;
import dev.betterchats.core.filter.FilterChain;
import dev.betterchats.core.format.FormatPipeline;
import dev.betterchats.core.format.LegacyColors;
import dev.betterchats.core.format.MessageSanitizer;
import dev.betterchats.core.format.TokenRegistry;
import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.Platform;
import dev.betterchats.core.platform.Scheduler;
import dev.betterchats.core.pm.ConversationTracker;
import dev.betterchats.core.pm.PrivateMessageService;
import dev.betterchats.core.spy.SpyServiceImpl;
import dev.betterchats.core.team.MembershipStore;
import dev.betterchats.core.team.TeamImpl;
import dev.betterchats.core.team.TeamRegistry;
import dev.betterchats.core.util.AtomicFiles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Nucleo de BetterChats: implementa la API y orquesta los servicios. No
 * conoce Bukkit; el adaptador le da una {@link Platform}.
 */
public final class BetterChatsCore implements BetterChatsAPI {

    public static final String API_VERSION = "1.0.0";
    private static final Pattern TEAM_ID = Pattern.compile("[a-z0-9_-]{1,32}");

    private final Platform platform;
    private final AtomicReference<ConfigBundle> config = new AtomicReference<>();
    private final EventBusImpl events;
    private final MembershipStore memberships;
    private final TeamRegistry teams;
    private final PlayerDataManager data;
    private final FilterChain filters;
    private final BuiltinFilters builtinFilters;
    private final TokenRegistry tokens;
    private final FormatPipeline pipeline = new FormatPipeline();
    private final SpyServiceImpl spy;
    private final PrivateMessageService pm;
    private final ChatStyleService chatStyle;
    private final SnapshotService snapshots;
    private final ChatService chat;
    private final CommandService commands;
    private final List<Scheduler.TaskHandle> tasks = new ArrayList<>();
    private ConfigWatcher watcher;

    public BetterChatsCore(Platform platform) {
        this.platform = platform;
        this.events = new EventBusImpl(platform.logger());
        this.memberships = new MembershipStore(platform.dataFolder(), platform.logger());
        this.teams = new TeamRegistry(memberships);
        this.data = new PlayerDataManager(new JsonStorageProvider(platform.dataFolder()), platform.logger());
        this.filters = new FilterChain(platform.logger());
        this.tokens = new TokenRegistry(platform.logger());
        this.builtinFilters = new BuiltinFilters(() -> config().main().antiSpam(),
                id -> "#pm".equals(id)
                        ? Optional.ofNullable(config().privateMessages().rateLimit())
                        : teams.get(id).flatMap(TeamImpl::getRateLimit),
                key -> config().lang().raw(key));
        this.spy = new SpyServiceImpl(platform, data, events, pipeline, this::config);
        MessageSanitizer sanitizer = new MessageSanitizer();
        this.chatStyle = new ChatStyleService(this);
        this.pm = new PrivateMessageService(platform, this::config, data, filters, events, sanitizer, tokens,
                pipeline, spy, new ConversationTracker(), chatStyle);
        this.snapshots = new SnapshotService(platform, this::config);
        this.chat = new ChatService(this, pipeline, sanitizer);
        this.commands = new CommandService(this);
    }

    // ------------------------------------------------------------------ ciclo

    /** Carga la configuracion. Si falla en el arranque se usan los valores por defecto. */
    public void enable() {
        ConfigLoader.Result result = loader(platform.dataFolder()).load();
        result.warnings().forEach(w -> platform.logger().warning("[BetterChats] " + w));
        if (!result.success()) {
            platform.logger().severe("[BetterChats] La configuracion tiene errores; se arranca con la configuracion por defecto:");
            result.errors().forEach(e -> platform.logger().severe("  - " + e));
            try {
                Path tmp = Files.createTempDirectory("betterchats-defaults");
                result = loader(tmp).load();
            } catch (IOException ex) {
                throw new IllegalStateException("No se pudo preparar la configuracion por defecto", ex);
            }
            if (!result.success()) {
                throw new IllegalStateException("La configuracion por defecto incluida no es valida: " + result.errors());
            }
        }
        config.set(result.bundle());
        memberships.load();
        teams.replaceFileTeams(result.bundle().teams());
        spy.registerBuiltins();

        filters.register(new dev.betterchats.core.filter.LookupFilter(
                () -> config().main().security().blockLookupPatterns(),
                () -> config().lang().raw("security-blocked")), dev.betterchats.core.filter.LookupFilter.PRIORITY);
        filters.register(builtinFilters.length, BuiltinFilters.LENGTH_PRIORITY);
        filters.register(builtinFilters.duplicate, BuiltinFilters.DUPLICATE_PRIORITY);
        filters.register(builtinFilters.rateLimit, BuiltinFilters.RATE_PRIORITY);

        // Los alias se registran en el primer tick, cuando todos los plugins ya
        // registraron los suyos, para poder respetar los ocupados.
        platform.scheduler().runSync(commands::registerAll);
        scheduleTasks();
        updateWatcher();
        for (ChatPlayer p : platform.players().online()) {
            data.loadAsync(p.uuid());
        }
        platform.logger().info("[BetterChats] Activo en " + platform.platformName() + " con " + teams.all().size()
                + " chats (API " + API_VERSION + ").");
    }

    public void disable() {
        tasks.forEach(Scheduler.TaskHandle::cancel);
        tasks.clear();
        if (watcher != null) watcher.close();
        commands.unregisterAll();
        memberships.saveIfDirty();
        data.shutdown();
    }

    private ConfigLoader loader(Path folder) {
        return new ConfigLoader(folder, platform::resource, platform.sounds()::validate);
    }

    private void scheduleTasks() {
        tasks.forEach(Scheduler.TaskHandle::cancel);
        tasks.clear();
        ConfigBundle cfg = config();
        long autosave = cfg.main().persistence().autosaveSeconds() * 20L;
        tasks.add(platform.scheduler().runRepeating(() -> {
            data.autosave(uuid -> platform.players().get(uuid).isPresent());
            data.io().execute(memberships::saveIfDirty);
        }, autosave, autosave, true));
        int refresh = cfg.main().snapshot().refreshTicks();
        tasks.add(platform.scheduler().runRepeating(snapshots::refreshAll, 1L, refresh, false));
    }

    private void updateWatcher() {
        boolean want = config().main().devAutoReload();
        if (want && watcher == null) {
            watcher = new ConfigWatcher(platform.dataFolder(), platform.logger(),
                    () -> platform.scheduler().runSync(() -> {
                        ReloadResult r = reload();
                        platform.logger().info("[BetterChats] Recarga automatica: " + (r.isSuccess() ? "OK" : "con errores " + r.getErrors()));
                    }));
            watcher.start();
        } else if (!want && watcher != null) {
            watcher.close();
            watcher = null;
        }
    }

    // ------------------------------------------------------ jugadores (adaptador)

    /** Hilo asincrono de prelogin: carga bloqueante permitida. */
    public void onPreLogin(UUID uuid) {
        try {
            data.loadAsync(uuid).join();
        } catch (RuntimeException ex) {
            platform.logger().log(Level.WARNING, "[BetterChats] Fallo la carga de datos de " + uuid, ex);
        }
    }

    /** Hilo principal. */
    public void onJoin(ChatPlayer player) {
        data.loadAsync(player.uuid()).thenRun(() -> spy.onJoin(player));
        snapshots.refresh(player);
    }

    public void onQuit(UUID uuid) {
        data.unload(uuid);
        pm.conversations().forget(uuid);
        builtinFilters.forget(uuid);
        snapshots.forget(uuid);
    }

    // ------------------------------------------------------------- internos

    public Platform platform() { return platform; }
    public ConfigBundle config() { return config.get(); }
    public EventBusImpl events() { return events; }
    public TeamRegistry teams() { return teams; }
    public PlayerDataManager data() { return data; }
    public FilterChain filters() { return filters; }
    public TokenRegistry tokens() { return tokens; }
    public SpyServiceImpl spy() { return spy; }
    public PrivateMessageService pm() { return pm; }
    public ChatStyleService chatStyle() { return chatStyle; }
    public SnapshotService snapshots() { return snapshots; }
    public ChatService chat() { return chat; }
    public MembershipStore memberships() { return memberships; }

    /** Chat activo; si ya no existe se usa el por defecto. Sin E/S. */
    public TeamImpl activeTeam(UUID player) {
        PlayerData d = data.cached(player);
        String id = d == null ? null : d.activeTeam();
        return teams.get(id).orElse(teams.defaultTeam());
    }

    /** Validacion de permisos en tiempo real (en cada mensaje). */
    public boolean hasAccess(ChatPlayer player, TeamImpl team) {
        if (team.isDefault()) return true;
        String perm = team.getPermission().orElse(null);
        if (perm != null && !player.hasPermission(perm)) return false;
        return team.hasImplicitMembers() || team.isMember(player.uuid());
    }

    public Component renderTeamName(TeamImpl team) {
        String name = team.getDisplayName();
        if (config().main().chat().legacyColorsInFormats()) name = LegacyColors.toMiniMessage(name);
        return MiniMessage.miniMessage().deserialize(name);
    }

    /** Cambio forzado al chat por defecto (no cancelable). */
    public void forceDefault(UUID player, TeamImpl previous, ChatActiveTeamChangeEvent.Reason reason) {
        PlayerData d = data.cached(player);
        if (d == null || d.activeTeam() == null) return;
        events.post(new ChatActiveTeamChangeEvent(player, previous, teams.defaultTeam(), reason));
        d.activeTeam(null);
    }

    /** Cambio de chat activo pedido por el jugador o por la API. */
    public boolean changeActive(UUID player, TeamImpl next, ChatActiveTeamChangeEvent.Reason reason) {
        TeamImpl previous = activeTeam(player);
        if (previous.getId().equals(next.getId())) return true;
        ChatActiveTeamChangeEvent ev = events.post(new ChatActiveTeamChangeEvent(player, previous, next, reason));
        if (ev.isCancelled()) return false;
        data.get(player).activeTeam(next.isDefault() ? null : next.getId());
        return true;
    }

    // ------------------------------------------------------------------- API

    @Override
    public String getApiVersion() {
        return API_VERSION;
    }

    @Override
    public Optional<ChatTeam> createTeam(TeamDefinition def) {
        if (!TEAM_ID.matcher(def.getId()).matches() || !def.getFormat().contains("{message}")) {
            platform.logger().warning("[BetterChats] createTeam: definicion invalida para '" + def.getId()
                    + "' (id en minusculas [a-z0-9_-] y formato con {message})");
            return Optional.empty();
        }
        if (def.isDefault() || teams.get(def.getId()).isPresent()) return Optional.empty();
        if (events.post(new ChatTeamCreateEvent(def)).isCancelled()) return Optional.empty();
        if (!teams.add(def)) return Optional.empty();
        if (def.isPersistent()) {
            JsonObject json = TeamJson.toJson(def);
            Path file = platform.dataFolder().resolve("chats").resolve(def.getId() + ".json");
            data.io().execute(() -> {
                try {
                    AtomicFiles.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
                            .create().toJson(json));
                } catch (IOException ex) {
                    platform.logger().log(Level.SEVERE, "[BetterChats] No se pudo guardar " + file, ex);
                }
            });
        }
        return teams.get(def.getId()).map(t -> t);
    }

    @Override
    public boolean deleteTeam(String teamId) {
        Optional<TeamImpl> found = teams.get(teamId);
        if (found.isEmpty() || found.get().isDefault()) return false;
        TeamImpl team = found.get();
        if (events.post(new ChatTeamDeleteEvent(team, team.getMembers())).isCancelled()) return false;
        teams.remove(teamId);
        for (UUID member : memberships.removeTeam(teamId)) {
            events.post(new ChatTeamLeaveEvent(member, team, MembershipCause.TEAM_DELETED));
        }
        for (ChatPlayer p : platform.players().online()) {
            PlayerData d = data.cached(p.uuid());
            if (d != null && teamId.equals(d.activeTeam())) {
                events.post(new ChatActiveTeamChangeEvent(p.uuid(), team, teams.defaultTeam(),
                        ChatActiveTeamChangeEvent.Reason.TEAM_DELETED));
                d.activeTeam(null);
            }
        }
        if (team.isPersistent()) {
            Path file = platform.dataFolder().resolve("chats").resolve(teamId + ".json");
            data.io().execute(() -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ex) {
                    platform.logger().log(Level.SEVERE, "[BetterChats] No se pudo borrar " + file, ex);
                }
            });
        }
        return true;
    }

    @Override
    public Optional<ChatTeam> getTeam(String teamId) {
        return teams.get(teamId).map(t -> t);
    }

    @Override
    public Collection<ChatTeam> getTeams() {
        return List.copyOf(teams.all());
    }

    @Override
    public ChatTeam getDefaultTeam() {
        return teams.defaultTeam();
    }

    @Override
    public ChatTeam getPlayerTeam(UUID player) {
        PlayerData d = data.cached(player);
        if (d == null) d = data.get(player);
        return teams.get(d.activeTeam()).orElse(teams.defaultTeam());
    }

    @Override
    public boolean setPlayerActiveTeam(UUID player, String teamId) {
        Optional<TeamImpl> team = teams.get(teamId);
        if (team.isEmpty()) return false;
        Optional<ChatPlayer> online = platform.players().get(player);
        boolean access = online.map(p -> hasAccess(p, team.get()))
                .orElse(team.get().isDefault() || team.get().isMember(player));
        if (!access) return false;
        return changeActive(player, team.get(), ChatActiveTeamChangeEvent.Reason.API);
    }

    @Override
    public boolean joinPlayerTeam(UUID player, String teamId) {
        return join(player, teamId, MembershipCause.API);
    }

    public boolean join(UUID player, String teamId, MembershipCause cause) {
        Optional<TeamImpl> team = teams.get(teamId);
        if (team.isEmpty() || team.get().isDefault() || team.get().isMember(player)) return false;
        if (events.post(new ChatTeamJoinEvent(player, team.get(), cause)).isCancelled()) return false;
        return memberships.add(teamId, player);
    }

    @Override
    public boolean kickPlayerFromTeam(UUID player, String teamId) {
        return leave(player, teamId, MembershipCause.API);
    }

    public boolean leave(UUID player, String teamId, MembershipCause cause) {
        Optional<TeamImpl> team = teams.get(teamId);
        if (team.isEmpty() || !team.get().isMember(player)) return false;
        if (events.post(new ChatTeamLeaveEvent(player, team.get(), cause)).isCancelled()) return false;
        memberships.remove(teamId, player);
        PlayerData d = data.cached(player);
        if (d != null && teamId.equals(d.activeTeam())) {
            Optional<ChatPlayer> online = platform.players().get(player);
            // Con miembros implicitos aun puede tener acceso por permiso.
            if (online.isEmpty() || !hasAccess(online.get(), team.get())) {
                forceDefault(player, team.get(), ChatActiveTeamChangeEvent.Reason.LEFT_TEAM);
            }
        }
        return true;
    }

    @Override
    public PrivateMessageResult sendPrivateMessage(UUID sender, UUID receiver, String message) {
        Optional<ChatPlayer> from = platform.players().get(sender);
        if (from.isEmpty()) return PrivateMessageResult.NOT_FOUND;
        return pm.send(from.get(), platform.players().get(receiver).orElse(null), message);
    }

    @Override
    public Optional<UUID> getLastConversationPartner(UUID player) {
        return pm.conversations().lastPartner(player);
    }

    @Override
    public boolean isIgnoring(UUID player, UUID target) {
        PlayerData d = data.cached(player);
        return (d != null ? d : data.get(player)).isIgnoring(target);
    }

    @Override
    public boolean ignore(UUID player, UUID target) {
        return !player.equals(target) && data.get(player).ignore(target);
    }

    @Override
    public boolean unignore(UUID player, UUID target) {
        return data.get(player).unignore(target);
    }

    @Override
    public Set<UUID> getIgnored(UUID player) {
        return data.get(player).ignored();
    }

    @Override
    public EventBus getEventBus() {
        return events;
    }

    @Override
    public SpyService getSpy() {
        return spy;
    }

    @Override
    public void registerFilter(ChatFilter filter, int priority) {
        filters.register(filter, priority);
    }

    @Override
    public void unregisterFilter(ChatFilter filter) {
        filters.unregister(filter);
    }

    @Override
    public void registerToken(MessageToken token) {
        tokens.register(token);
    }

    @Override
    public void unregisterToken(String tokenId) {
        tokens.unregister(tokenId);
    }

    /** Recarga transaccional: si algo falla se conserva la configuracion anterior. */
    @Override
    public synchronized ReloadResult reload() {
        ConfigLoader.Result result = loader(platform.dataFolder()).load();
        ReloadResult out;
        if (result.success()) {
            config.set(result.bundle());
            teams.replaceFileTeams(result.bundle().teams());
            spy.registerBuiltins();
            platform.scheduler().runSync(() -> {
                commands.unregisterAll();
                commands.registerAll();
            });
            scheduleTasks();
            updateWatcher();
            out = new ReloadResult(true, List.of(), result.warnings());
        } else {
            out = new ReloadResult(false, result.errors(), result.warnings());
        }
        events.post(new BetterChatsReloadEvent(out));
        return out;
    }
}
