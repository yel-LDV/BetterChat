package dev.betterchats.core.pm;

import dev.betterchats.api.PrivateMessageResult;
import dev.betterchats.core.ChatStyleService;
import dev.betterchats.api.event.EventBus;
import dev.betterchats.api.event.PrivateMessageEvent;
import dev.betterchats.api.filter.ChatContext;
import dev.betterchats.api.filter.MessageKind;
import dev.betterchats.api.spy.SpyMessage;
import dev.betterchats.core.config.ConfigBundle;
import dev.betterchats.core.config.Lang;
import dev.betterchats.core.config.PrivateMessagesConfig;
import dev.betterchats.core.data.PlayerDataManager;
import dev.betterchats.core.filter.FilterChain;
import dev.betterchats.core.format.FormatPipeline;
import dev.betterchats.core.format.MessageSanitizer;
import dev.betterchats.core.format.TagPolicy;
import dev.betterchats.core.format.TokenRegistry;
import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.Platform;
import dev.betterchats.core.spy.SpyServiceImpl;
import dev.betterchats.core.util.PacketSafety;
import dev.betterchats.core.util.Texts;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Mensajes privados (seccion 9 del plan). Todas las respuestas al emisor
 * (no encontrado, ignorado, filtrado...) salen de aqui, asi un PM enviado por
 * la API se comporta igual que uno enviado con /msg.
 */
public final class PrivateMessageService {

    public static final String IGNORE_BYPASS = "betterchats.ignore.bypass";

    private final Platform platform;
    private final Supplier<ConfigBundle> config;
    private final PlayerDataManager data;
    private final FilterChain filters;
    private final EventBus events;
    private final MessageSanitizer sanitizer;
    private final TokenRegistry tokens;
    private final FormatPipeline pipeline;
    private final SpyServiceImpl spy;
    private final ConversationTracker conversations;
    private final ChatStyleService chatStyle;

    public PrivateMessageService(Platform platform, Supplier<ConfigBundle> config, PlayerDataManager data,
                                 FilterChain filters, EventBus events, MessageSanitizer sanitizer,
                                 TokenRegistry tokens, FormatPipeline pipeline, SpyServiceImpl spy,
                                 ConversationTracker conversations, ChatStyleService chatStyle) {
        this.chatStyle = chatStyle;
        this.platform = platform;
        this.config = config;
        this.data = data;
        this.filters = filters;
        this.events = events;
        this.sanitizer = sanitizer;
        this.tokens = tokens;
        this.pipeline = pipeline;
        this.spy = spy;
        this.conversations = conversations;
    }

    public ConversationTracker conversations() {
        return conversations;
    }

    /** true si {@code viewer} no deberia saber que {@code target} esta conectado. */
    public boolean hiddenFrom(ChatPlayer viewer, ChatPlayer target) {
        PrivateMessagesConfig pm = config.get().privateMessages();
        if (!pm.respectVanish() || viewer.uuid().equals(target.uuid())) return false;
        if (!platform.vanish().isVanished(target)) return false;
        if (viewer.hasPermission(pm.vanishSeePermission())) return false;
        return !platform.vanish().canSee(viewer, target);
    }

    public PrivateMessageResult send(ChatPlayer sender, ChatPlayer receiver, String rawMessage) {
        ConfigBundle cfg = config.get();
        PrivateMessagesConfig pm = cfg.privateMessages();
        Lang lang = cfg.lang();

        if (!pm.enabled()) {
            sender.sendMessage(lang.get("pm-disabled"));
            return PrivateMessageResult.DISABLED;
        }
        String message = rawMessage == null ? "" : rawMessage.strip();
        if (message.isEmpty()) {
            sender.sendMessage(lang.get("pm-empty"));
            return PrivateMessageResult.EMPTY;
        }
        if (receiver == null || !receiver.isOnline() || hiddenFrom(sender, receiver)) {
            sender.sendMessage(lang.get("player-not-found"));
            return PrivateMessageResult.NOT_FOUND;
        }
        if (sender.uuid().equals(receiver.uuid()) && !pm.allowSelfMessage()) {
            sender.sendMessage(lang.get("pm-self"));
            return PrivateMessageResult.SELF;
        }
        if (data.get(sender.uuid()).isIgnoring(receiver.uuid())) {
            sender.sendMessage(lang.get("pm-sender-ignores", Lang.unparsed("player", receiver.name())));
            return PrivateMessageResult.SENDER_IGNORES;
        }

        List<String> lines = message.lines().filter(l -> !l.isBlank()).toList();
        FilterChain.Outcome filtered = filters.run(new ChatContext(sender.ref(), MessageKind.PRIVATE, null,
                receiver.ref(), String.join(" ", lines), lines.size(), System.currentTimeMillis(),
                sender::hasPermission));
        if (filtered.cancelled()) {
            if (filtered.feedback() != null) sender.sendMessage(lang.render(filtered.feedback()));
            return PrivateMessageResult.FILTERED;
        }

        PrivateMessageEvent event = events.post(new PrivateMessageEvent(sender.ref(), receiver.ref(),
                filtered.message()));
        if (event.isCancelled()) return PrivateMessageResult.CANCELLED;
        String finalMessage = event.getMessage();

        Set<String> allowed = TagPolicy.allowedTags(Set.of(), cfg.main().security().defaultAllowedPlayerTags(),
                cfg.main().security().bypassPermission(), sender::hasPermission);
        Component body = sanitizer.sanitize(finalMessage, allowed, true);
        var applied = tokens.apply(body, finalMessage, sender.ref(), sender::hasPermission);
        body = chatStyle.apply(sender, applied.body(), applied.replaced(), true);

        boolean legacy = cfg.main().chat().legacyColorsInFormats();
        Map<String, String> names = Map.of("sender", sender.name(), "receiver", receiver.name());
        Map<String, Component> comps = Map.of("message", body);
        Component outgoing = PacketSafety.limit(pipeline.render(pm.formatOutgoing(), names, comps, receiver,
                platform.placeholders(), null, legacy));

        boolean ignored = data.get(receiver.uuid()).isIgnoring(sender.uuid()) && !sender.hasPermission(IGNORE_BYPASS);
        if (ignored) {
            if (pm.notifyIgnoredSender()) {
                sender.sendMessage(lang.get("pm-ignored", Lang.unparsed("player", receiver.name())));
            } else {
                // Descartado en silencio: el emisor ve su mensaje como si hubiera salido.
                sender.sendMessage(outgoing);
            }
            return PrivateMessageResult.IGNORED;
        }

        Component incoming = PacketSafety.limit(pipeline.render(pm.formatIncoming(), names, comps, sender,
                platform.placeholders(), null, legacy));
        receiver.sendMessage(incoming);
        if (!sender.uuid().equals(receiver.uuid())) sender.sendMessage(outgoing);
        conversations.record(sender.uuid(), receiver.uuid());

        if (pm.sound().isEnabled()) {
            platform.scheduler().runForPlayer(receiver, () -> platform.sounds().play(receiver, pm.sound()));
        }

        spy.dispatch(SpyMessage.builder(SpyServiceImpl.PM)
                .sender(sender.uuid(), sender.name())
                .target(receiver.name())
                .message(Texts.plain(body))
                .alreadyReceived(sender.uuid())
                .alreadyReceived(receiver.uuid())
                .build());
        return PrivateMessageResult.SENT;
    }
}
