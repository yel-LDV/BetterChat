package dev.betterchats.core.command;

import dev.betterchats.api.spy.SpyChannel;
import dev.betterchats.core.BetterChatsCore;
import dev.betterchats.core.config.Lang;
import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.CommandSource;
import dev.betterchats.core.platform.CommandSpec;
import dev.betterchats.core.spy.SpyServiceImpl;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * /spy               activa/desactiva el spy en todos tus canales
 * /spy list          canales, permiso y estado
 * /spy &lt;canal&gt;       alterna un canal (pm, team, velezcore.msg, vclans.clan, vclans.ally...)
 * /spy on|off        fuerza el estado de todos
 */
final class SpyCommand implements CommandSpec.CoreCommand {

    private final BetterChatsCore core;

    SpyCommand(BetterChatsCore core) {
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
        SpyServiceImpl spy = core.spy();
        List<SpyChannel> allowed = new ArrayList<>();
        for (SpyChannel ch : spy.getChannels()) {
            if (spy.isChannelEnabled(ch) && spy.canSpy(player, ch)) allowed.add(ch);
        }
        if (allowed.isEmpty()) {
            source.sendMessage(lang.get("spy-no-channels"));
            return;
        }

        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> toggleAll(player, allowed, spy.activeFor(player).isEmpty());
            case "on", "all" -> toggleAll(player, allowed, true);
            case "off", "none" -> toggleAll(player, allowed, false);
            case "list" -> list(player, spy);
            default -> {
                Optional<SpyChannel> ch = spy.getChannel(sub);
                if (ch.isEmpty() || !spy.isChannelEnabled(ch.get())) {
                    source.sendMessage(lang.get("spy-unknown-channel", Lang.unparsed("channel", sub)));
                    return;
                }
                if (!spy.canSpy(player, ch.get())) {
                    source.sendMessage(lang.get("spy-channel-no-permission",
                            Lang.unparsed("channel", ch.get().getId())));
                    return;
                }
                boolean on = !spy.isSpying(player.uuid(), ch.get().getId());
                spy.setSpying(player.uuid(), ch.get().getId(), on);
                source.sendMessage(lang.get(on ? "spy-channel-on" : "spy-channel-off",
                        Lang.unparsed("channel", ch.get().getDisplayName())));
            }
        }
    }

    private void toggleAll(ChatPlayer player, List<SpyChannel> allowed, boolean on) {
        Lang lang = core.config().lang();
        core.spy().setSpying(player.uuid(), SpyServiceImpl.ALL, on);
        if (on) {
            List<String> names = new ArrayList<>();
            for (SpyChannel ch : allowed) names.add(ch.getDisplayName());
            player.sendMessage(lang.get("spy-all-on", Lang.unparsed("list", String.join(", ", names))));
        } else {
            player.sendMessage(lang.get("spy-all-off"));
        }
    }

    private void list(ChatPlayer player, SpyServiceImpl spy) {
        Lang lang = core.config().lang();
        player.sendMessage(lang.get("spy-list-header"));
        for (SpyChannel ch : spy.getChannels()) {
            String statusKey;
            if (!spy.isChannelEnabled(ch) || !spy.canSpy(player, ch)) statusKey = "spy-status-disabled";
            else statusKey = spy.isSpying(player.uuid(), ch.getId()) ? "spy-status-on" : "spy-status-off";
            Component status = lang.get(statusKey);
            player.sendMessage(lang.get("spy-list-line", Lang.component("status", status),
                    Lang.unparsed("id", ch.getId()), Lang.unparsed("name", ch.getDisplayName())));
        }
    }

    @Override
    public List<String> complete(CommandSource source, String[] args) {
        if (args.length != 1 || source.player().isEmpty()) return List.of();
        List<String> opts = new ArrayList<>(List.of("list", "on", "off"));
        for (SpyChannel ch : core.spy().getChannels()) {
            if (core.spy().canSpy(source.player().get(), ch)) opts.add(ch.getId());
        }
        return Commands.filter(opts, args[0]);
    }
}
