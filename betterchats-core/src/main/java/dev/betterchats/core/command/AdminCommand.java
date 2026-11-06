package dev.betterchats.core.command;

import dev.betterchats.api.ReloadResult;
import dev.betterchats.api.event.MembershipCause;
import dev.betterchats.core.BetterChatsCore;
import dev.betterchats.core.config.Lang;
import dev.betterchats.core.platform.CommandSource;
import dev.betterchats.core.platform.CommandSpec;
import dev.betterchats.core.team.TeamImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** /betterchats [reload|version|teams|info|join|leave|help]. */
final class AdminCommand implements CommandSpec.CoreCommand {

    private static final List<String> SUBS = List.of("help", "reload", "version", "teams", "info", "join", "leave");

    private final BetterChatsCore core;

    AdminCommand(BetterChatsCore core) {
        this.core = core;
    }

    @Override
    public void execute(CommandSource source, String label, String[] args) {
        Lang lang = core.config().lang();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                ReloadResult r = core.reload();
                Lang now = core.config().lang();
                if (r.isSuccess()) {
                    source.sendMessage(now.get("reload-ok"));
                } else {
                    source.sendMessage(now.get("reload-failed"));
                    r.getErrors().forEach(e -> source.sendMessage(now.get("reload-line", Lang.unparsed("line", e))));
                }
                if (!r.getWarnings().isEmpty()) {
                    source.sendMessage(now.get("reload-warnings", Lang.unparsed("count", String.valueOf(r.getWarnings().size()))));
                    r.getWarnings().forEach(w -> source.sendMessage(now.get("reload-line", Lang.unparsed("line", w))));
                }
            }
            case "version" -> source.sendMessage(lang.get("version",
                    Lang.unparsed("version", BetterChatsCore.API_VERSION),
                    Lang.unparsed("platform", core.platform().platformName()),
                    Lang.unparsed("papi", core.platform().placeholders().isAvailable() ? "si" : "no")));
            case "teams" -> {
                source.sendMessage(lang.get("teams-header"));
                for (TeamImpl t : core.teams().all()) {
                    source.sendMessage(lang.get("admin-team-line",
                            Lang.component("team", core.renderTeamName(t)),
                            Lang.unparsed("id", t.getId()),
                            Lang.unparsed("members", t.hasImplicitMembers() ? "permiso"
                                    : String.valueOf(t.getMembers().size())),
                            Lang.unparsed("permission", t.getPermission().orElse("-")),
                            Lang.unparsed("priority", String.valueOf(t.getPriority()))));
                }
            }
            case "info" -> {
                if (args.length < 2) {
                    source.sendMessage(lang.get("usage", Lang.unparsed("usage", "/" + label + " info <chat>")));
                    return;
                }
                Optional<TeamImpl> t = core.teams().get(args[1].toLowerCase(Locale.ROOT));
                if (t.isEmpty()) {
                    source.sendMessage(lang.get("team-not-found", Lang.unparsed("team", args[1])));
                    return;
                }
                List<String> names = new ArrayList<>();
                for (UUID u : t.get().getMembers()) names.add(Commands.nameOf(core, u));
                source.sendMessage(lang.get("admin-team-info",
                        Lang.component("team", core.renderTeamName(t.get())),
                        Lang.unparsed("id", t.get().getId()),
                        Lang.unparsed("permission", t.get().getPermission().orElse("-")),
                        Lang.unparsed("format", t.get().getFormat()),
                        Lang.unparsed("tags", String.join(", ", t.get().getAllowedPlayerTags())),
                        Lang.unparsed("ratelimit", t.get().getRateLimit().map(Object::toString).orElse("-")),
                        Lang.unparsed("members", names.isEmpty() ? "-" : String.join(", ", names))));
            }
            case "join", "leave" -> {
                if (args.length < 3) {
                    source.sendMessage(lang.get("usage", Lang.unparsed("usage",
                            "/" + label + " " + sub + " <jugador> <chat>")));
                    return;
                }
                Optional<UUID> target = Commands.findAny(core, source, args[1]);
                if (target.isEmpty()) {
                    source.sendMessage(lang.get("player-not-found"));
                    return;
                }
                Optional<TeamImpl> t = core.teams().get(args[2].toLowerCase(Locale.ROOT));
                if (t.isEmpty() || t.get().isDefault()) {
                    source.sendMessage(lang.get("team-not-found", Lang.unparsed("team", args[2])));
                    return;
                }
                String name = Commands.nameOf(core, target.get());
                boolean ok = sub.equals("join")
                        ? core.join(target.get(), t.get().getId(), MembershipCause.COMMAND)
                        : core.leave(target.get(), t.get().getId(), MembershipCause.COMMAND);
                String key = sub.equals("join")
                        ? (ok ? "team-joined-admin" : "team-already-member")
                        : (ok ? "team-left-admin" : "team-not-member");
                source.sendMessage(lang.get(key, Lang.unparsed("player", name),
                        Lang.component("team", core.renderTeamName(t.get()))));
            }
            default -> source.sendMessage(lang.get("admin-help", Lang.unparsed("label", label)));
        }
    }

    @Override
    public List<String> complete(CommandSource source, String[] args) {
        if (args.length == 1) return Commands.filter(SUBS, args[0]);
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ((sub.equals("join") || sub.equals("leave")) && args.length == 2) {
            return Commands.onlineNames(core, source, args[1]);
        }
        if ((sub.equals("info") && args.length == 2) || ((sub.equals("join") || sub.equals("leave")) && args.length == 3)) {
            List<String> ids = new ArrayList<>();
            for (TeamImpl t : core.teams().all()) ids.add(t.getId());
            return Commands.filter(ids, args[args.length - 1]);
        }
        return List.of();
    }
}
