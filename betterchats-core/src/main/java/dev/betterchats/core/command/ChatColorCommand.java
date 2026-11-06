package dev.betterchats.core.command;

import dev.betterchats.core.BetterChatsCore;
import dev.betterchats.core.ChatStyleService;
import dev.betterchats.core.config.ChatColorsConfig;
import dev.betterchats.core.config.Lang;
import dev.betterchats.core.format.FormatPipeline;
import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.CommandSource;
import dev.betterchats.core.platform.CommandSpec;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * /chatcolor            abre el menu (PacketEvents); sin menu, lista las opciones
 * /chatcolor &lt;id&gt;       alterna un color/efecto o un formato
 * /chatcolor list|reset
 */
final class ChatColorCommand implements CommandSpec.CoreCommand {

    private final BetterChatsCore core;

    ChatColorCommand(BetterChatsCore core) {
        this.core = core;
    }

    @Override
    public void execute(CommandSource source, String label, String[] args) {
        Lang lang = core.config().lang();
        Optional<ChatPlayer> self = source.player();
        if (self.isEmpty()) {
            source.sendMessage(lang.get("player-only"));
            return;
        }
        ChatPlayer player = self.get();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> {
                if (!core.platform().openChatColorMenu(player)) list(player);
            }
            case "list" -> list(player);
            case "reset", "off" -> {
                core.chatStyle().reset(player);
                player.sendMessage(lang.get("chatcolor-reset"));
            }
            default -> {
                ChatStyleService.Result r = core.chatStyle().toggle(player, sub);
                player.sendMessage(feedback(lang, r, sub));
            }
        }
    }

    /** Mensaje para el resultado de una seleccion (lo usa tambien el menu). */
    static Component feedback(Lang lang, ChatStyleService.Result r, String name) {
        return feedback(lang, r, Component.text(name));
    }

    static Component feedback(Lang lang, ChatStyleService.Result r, Component name) {
        return switch (r) {
            case SELECTED -> lang.get("chatcolor-selected", Lang.component("name", name));
            case REMOVED -> lang.get("chatcolor-removed", Lang.component("name", name));
            case LOCKED -> lang.get("chatcolor-locked", Lang.component("name", name));
            case UNKNOWN -> lang.get("chatcolor-unknown", Lang.component("name", name));
            case DISABLED -> lang.get("chatcolor-disabled");
        };
    }

    private void list(ChatPlayer player) {
        Lang lang = core.config().lang();
        ChatColorsConfig cfg = core.config().chatColors();
        player.sendMessage(lang.get("chatcolor-list-header"));
        for (ChatColorsConfig.ColorOption c : cfg.colors()) line(player, lang, c.id(), c.item().name(), c.permission());
        for (ChatColorsConfig.FormatOption f : cfg.formats()) line(player, lang, f.id(), f.item().name(), f.permission());
    }

    private void line(ChatPlayer player, Lang lang, String id, String name, String permission) {
        String key = !core.chatStyle().canUse(player, permission) ? "spy-status-disabled"
                : core.chatStyle().isSelected(player, id) ? "spy-status-on" : "spy-status-off";
        player.sendMessage(lang.get("chatcolor-list-line", Lang.component("status", lang.get(key)),
                Lang.unparsed("id", id), Lang.component("name", FormatPipeline.legacyToComponent(name))));
    }

    @Override
    public List<String> complete(CommandSource source, String[] args) {
        if (args.length != 1) return List.of();
        List<String> opts = new ArrayList<>(List.of("list", "reset"));
        ChatColorsConfig cfg = core.config().chatColors();
        cfg.colors().forEach(c -> opts.add(c.id()));
        cfg.formats().forEach(f -> opts.add(f.id()));
        return Commands.filter(opts, args[0]);
    }
}
