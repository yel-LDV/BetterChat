package dev.betterchats.core.command;

import dev.betterchats.core.BetterChatsCore;
import dev.betterchats.core.config.Lang;
import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.CommandSource;
import dev.betterchats.core.platform.CommandSpec;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** /msg &lt;jugador&gt; &lt;mensaje&gt; y /reply &lt;mensaje&gt;. */
final class MsgCommand implements CommandSpec.CoreCommand {

    private final BetterChatsCore core;
    private final boolean reply;

    MsgCommand(BetterChatsCore core, boolean reply) {
        this.core = core;
        this.reply = reply;
    }

    @Override
    public void execute(CommandSource source, String label, String[] args) {
        Lang lang = core.config().lang();
        Optional<ChatPlayer> sender = source.player();
        if (sender.isEmpty()) {
            source.sendMessage(lang.get("player-only"));
            return;
        }
        if (reply) {
            if (args.length < 1) {
                source.sendMessage(lang.get("usage", Lang.unparsed("usage", "/" + label + " <mensaje>")));
                return;
            }
            Optional<UUID> last = core.pm().conversations().lastPartner(sender.get().uuid());
            Optional<ChatPlayer> target = last.flatMap(core.platform().players()::get);
            if (target.isEmpty()) {
                source.sendMessage(lang.get("pm-no-reply"));
                return;
            }
            core.pm().send(sender.get(), target.get(), Commands.join(args, 0));
            return;
        }
        if (args.length < 2) {
            source.sendMessage(lang.get("usage", Lang.unparsed("usage", "/" + label + " <jugador> <mensaje>")));
            return;
        }
        ChatPlayer target = Commands.findOnline(core, source, args[0]).orElse(null);
        core.pm().send(sender.get(), target, Commands.join(args, 1));
    }

    @Override
    public List<String> complete(CommandSource source, String[] args) {
        if (!reply && args.length == 1) return Commands.onlineNames(core, source, args[0]);
        return List.of();
    }
}
