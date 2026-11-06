package dev.betterchats.core.command;

import dev.betterchats.api.event.ChatActiveTeamChangeEvent;
import dev.betterchats.core.BetterChatsCore;
import dev.betterchats.core.config.Lang;
import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.CommandSource;
import dev.betterchats.core.platform.CommandSpec;
import dev.betterchats.core.team.TeamImpl;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * /chat                 lista los chats a los que tienes acceso
 * /chat &lt;id&gt;            cambia el chat activo (repetirlo vuelve al por defecto)
 * /chat &lt;id&gt; &lt;mensaje&gt;  escribe una vez en ese chat sin cambiar el activo
 */
final class ChatCommand implements CommandSpec.CoreCommand {

    private final BetterChatsCore core;

    ChatCommand(BetterChatsCore core) {
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
        if (args.length == 0) {
            TeamImpl active = core.activeTeam(player.uuid());
            source.sendMessage(lang.get("teams-header"));
            for (TeamImpl t : core.teams().all()) {
                if (!core.hasAccess(player, t)) continue;
                source.sendMessage(lang.get("teams-line",
                        Lang.component("team", core.renderTeamName(t)),
                        Lang.unparsed("id", t.getId()),
                        Lang.component("active", t.getId().equals(active.getId())
                                ? lang.get("teams-active-mark") : Component.empty())));
            }
            return;
        }
        Optional<TeamImpl> found = core.teams().get(args[0].toLowerCase(java.util.Locale.ROOT));
        if (found.isEmpty()) {
            source.sendMessage(lang.get("team-not-found", Lang.unparsed("team", args[0])));
            return;
        }
        TeamImpl team = found.get();
        if (!core.hasAccess(player, team)) {
            source.sendMessage(lang.get("team-no-access", Lang.component("team", core.renderTeamName(team))));
            return;
        }
        if (args.length > 1) {
            String message = Commands.join(args, 1);
            core.platform().scheduler().runAsync(() -> core.chat().sendDirect(player, team, message));
            return;
        }
        TeamImpl target = core.activeTeam(player.uuid()).getId().equals(team.getId()) && !team.isDefault()
                ? core.teams().defaultTeam() : team;
        if (core.changeActive(player.uuid(), target, ChatActiveTeamChangeEvent.Reason.COMMAND)) {
            source.sendMessage(lang.get("team-switched", Lang.component("team", core.renderTeamName(target))));
        } else {
            source.sendMessage(lang.get("team-switch-cancelled"));
        }
    }

    @Override
    public List<String> complete(CommandSource source, String[] args) {
        if (args.length != 1 || source.player().isEmpty()) return List.of();
        List<String> ids = new ArrayList<>();
        for (TeamImpl t : core.teams().all()) {
            if (core.hasAccess(source.player().get(), t)) ids.add(t.getId());
        }
        return Commands.filter(ids, args[0]);
    }
}
