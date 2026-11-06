package dev.betterchats.core;

import dev.betterchats.api.event.ChatMessageEvent;
import dev.betterchats.api.filter.ChatContext;
import dev.betterchats.api.filter.MessageKind;
import dev.betterchats.api.spy.SpyMessage;
import dev.betterchats.core.config.ConfigBundle;
import dev.betterchats.core.config.MainConfig;
import dev.betterchats.core.data.PlayerData;
import dev.betterchats.core.format.FormatPipeline;
import dev.betterchats.core.format.MessageSanitizer;
import dev.betterchats.core.format.TagPolicy;
import dev.betterchats.core.format.TokenRegistry;
import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.PlayerSnapshot;
import dev.betterchats.core.pm.PrivateMessageService;
import dev.betterchats.core.spy.SpyServiceImpl;
import dev.betterchats.core.team.TeamImpl;
import dev.betterchats.core.util.Texts;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pipeline de chat (seccion 5 del plan): chat activo -> permisos en tiempo
 * real -> filtros -> ChatMessageEvent -> destinatarios -> formato seguro.
 * La entrega la hace el adaptador segun la estrategia.
 */
public final class ChatService {

    /**
     * @param cancelled  true = no se entrega nada (filtro, evento, sin acceso)
     * @param rich       el mensaje lleva hover/clic (tokens o etiquetas protegidas)
     * @param recipients destinatarios ya resueltos (incluye al emisor)
     */
    public record Outcome(boolean cancelled, TeamImpl team, Component line, List<ChatPlayer> recipients,
                          boolean rich) {

        static Outcome cancel() {
            return new Outcome(true, null, Component.empty(), List.of(), false);
        }
    }

    private final BetterChatsCore core;
    private final MessageSanitizer sanitizer;
    private final FormatPipeline pipeline;

    ChatService(BetterChatsCore core, FormatPipeline pipeline, MessageSanitizer sanitizer) {
        this.core = core;
        this.pipeline = pipeline;
        this.sanitizer = sanitizer;
    }

    public MessageSanitizer sanitizer() {
        return sanitizer;
    }

    /** Se llama en el hilo asincrono del chat. */
    public Outcome process(ChatPlayer sender, String rawMessage) {
        return process(sender, rawMessage, null);
    }

    /**
     * Envia un mensaje a un chat concreto sin cambiar el activo
     * ({@code /chat staff hola}). Entrega con componentes propios.
     */
    public void sendDirect(ChatPlayer sender, TeamImpl team, String rawMessage) {
        Outcome o = process(sender, rawMessage, team);
        if (o.cancelled()) return;
        for (ChatPlayer p : o.recipients()) p.sendMessage(o.line());
        core.platform().sendConsole(o.line());
        playSounds(sender, o);
    }

    private Outcome process(ChatPlayer sender, String rawMessage, TeamImpl forced) {
        ConfigBundle cfg = core.config();
        PlayerSnapshot snapshot = core.snapshots().get(sender);

        // 2) chat activo (cache en memoria)
        TeamImpl team = forced != null ? forced : core.activeTeam(sender.uuid());

        if (forced != null && !core.hasAccess(sender, forced)) {
            sender.sendMessage(cfg.lang().get("team-no-access",
                    dev.betterchats.core.config.Lang.component("team", core.renderTeamName(forced))));
            return Outcome.cancel();
        }

        // 3) permisos en tiempo real
        if (!team.isDefault() && !core.hasAccess(sender, team)) {
            core.forceDefault(sender.uuid(), team, dev.betterchats.api.event.ChatActiveTeamChangeEvent.Reason.PERMISSION_LOST);
            sender.sendMessage(cfg.lang().render(cfg.main().chat().permissionLostMessage()));
            if (!cfg.main().chat().fallbackToDefaultOnPermissionLoss()) return Outcome.cancel();
            team = core.teams().defaultTeam();
        }

        // 4) anti-spam / filtros
        var filtered = core.filters().run(new ChatContext(sender.ref(), MessageKind.TEAM, team.getId(), null,
                rawMessage, 1, System.currentTimeMillis(), sender::hasPermission));
        if (filtered.cancelled()) {
            if (filtered.feedback() != null) sender.sendMessage(cfg.lang().render(filtered.feedback()));
            return Outcome.cancel();
        }

        // 6) destinatarios (se calculan antes del evento para que pueda modificarlos)
        Set<UUID> recipientIds = new LinkedHashSet<>();
        for (ChatPlayer p : recipients(sender, team)) recipientIds.add(p.uuid());

        // 5) evento publico
        ChatMessageEvent event = core.events().post(new ChatMessageEvent(sender.ref(), team, filtered.message(),
                recipientIds));
        if (event.isCancelled()) return Outcome.cancel();
        String message = event.getMessage();

        List<ChatPlayer> recipients = new ArrayList<>();
        for (UUID id : event.getRecipients()) core.platform().players().get(id).ifPresent(recipients::add);

        // 7) formato seguro
        MainConfig.Security sec = cfg.main().security();
        Set<String> allowed = TagPolicy.allowedTags(team.getAllowedPlayerTags(), sec.defaultAllowedPlayerTags(),
                sec.bypassPermission(), sender::hasPermission);
        Component body = sanitizer.sanitize(message, allowed, true);
        TokenRegistry.Applied tokens = core.tokens().apply(body, message, sender.ref(), sender::hasPermission);
        body = core.chatStyle().apply(sender, tokens.body(), tokens.replaced(), false);
        boolean rich = tokens.replaced() || allowed.contains("click") || allowed.contains("hover")
                || allowed.contains("insertion");

        Component line = pipeline.render(team.getFormat(), textPlaceholders(snapshot, team),
                Map.of("message", body,
                        "displayName", FormatPipeline.legacyToComponent(snapshot.displayName()),
                        "team", core.renderTeamName(team)),
                sender, core.platform().placeholders(), snapshot, cfg.main().chat().legacyColorsInFormats());
        line = dev.betterchats.core.util.PacketSafety.limit(line);

        // Spy de chats privados: el staff que no los lee puede verlos.
        if (!team.isDefault()) {
            core.spy().dispatch(SpyMessage.builder(SpyServiceImpl.TEAM)
                    .sender(sender.uuid(), sender.name())
                    .target(Texts.plain(core.renderTeamName(team)))
                    .message(Texts.plain(body))
                    .alreadyReceived(event.getRecipients())
                    .placeholder("team", team.getId())
                    .build());
        }
        return new Outcome(false, team, line, recipients, rich);
    }

    /** Reproduce el sonido del chat a cada destinatario (menos al emisor), en su hilo. */
    public void playSounds(ChatPlayer sender, Outcome outcome) {
        if (outcome.cancelled() || !outcome.team().getSoundConfig().isEnabled()) return;
        var sound = outcome.team().getSoundConfig();
        for (ChatPlayer p : outcome.recipients()) {
            if (p.uuid().equals(sender.uuid())) continue;
            core.platform().scheduler().runForPlayer(p, () -> core.platform().sounds().play(p, sound));
        }
    }

    private static Map<String, String> textPlaceholders(PlayerSnapshot s, TeamImpl team) {
        Map<String, String> m = new HashMap<>();
        m.put("playerName", s.name());
        var loc = s.location();
        m.put("world", loc.world());
        m.put("x", String.valueOf((int) Math.floor(loc.x())));
        m.put("y", String.valueOf((int) Math.floor(loc.y())));
        m.put("z", String.valueOf((int) Math.floor(loc.z())));
        m.put("player_location", String.format(Locale.ROOT, "%s, %d, %d, %d", loc.world(),
                (int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z())));
        return m;
    }

    /**
     * Destinatarios: chat por defecto = todos; chat privado/staff = miembros
     * (o todos con permiso si tiene miembros implicitos). Se excluye a quien
     * ignora al emisor, salvo que el emisor tenga el bypass.
     */
    public List<ChatPlayer> recipients(ChatPlayer sender, TeamImpl team) {
        boolean bypassIgnore = sender.hasPermission(PrivateMessageService.IGNORE_BYPASS);
        List<ChatPlayer> out = new ArrayList<>();
        for (ChatPlayer p : core.platform().players().online()) {
            if (p.uuid().equals(sender.uuid())) {
                out.add(p);
                continue;
            }
            if (!team.isDefault() && !core.hasAccess(p, team)) continue;
            if (!bypassIgnore) {
                PlayerData d = core.data().cached(p.uuid());
                if (d != null && d.isIgnoring(sender.uuid())) continue;
            }
            out.add(p);
        }
        return out;
    }
}
