package dev.betterchats.core.command;

import dev.betterchats.core.BetterChatsCore;
import dev.betterchats.core.config.Lang;
import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.CommandSource;
import dev.betterchats.core.platform.CommandSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** /ignore [jugador] (sin argumentos lista) y /unignore &lt;jugador&gt;. */
final class IgnoreCommand implements CommandSpec.CoreCommand {

    /** Quien tenga este permiso no puede ser ignorado (staff). */
    static final String EXEMPT = "betterchats.ignore.exempt";

    private final BetterChatsCore core;
    private final boolean ignore;

    IgnoreCommand(BetterChatsCore core, boolean ignore) {
        this.core = core;
        this.ignore = ignore;
    }

    @Override
    public void execute(CommandSource source, String label, String[] args) {
        Lang lang = core.config().lang();
        Optional<ChatPlayer> self = source.player();
        if (self.isEmpty()) {
            source.sendMessage(lang.get("player-only"));
            return;
        }
        UUID me = self.get().uuid();
        if (args.length == 0) {
            if (!ignore) {
                source.sendMessage(lang.get("usage", Lang.unparsed("usage", "/" + label + " <jugador>")));
                return;
            }
            Set<UUID> list = core.getIgnored(me);
            if (list.isEmpty()) {
                source.sendMessage(lang.get("ignore-list-empty"));
                return;
            }
            List<String> names = new ArrayList<>();
            for (UUID u : list) names.add(Commands.nameOf(core, u));
            names.sort(String.CASE_INSENSITIVE_ORDER);
            source.sendMessage(lang.get("ignore-list-header", Lang.unparsed("count", String.valueOf(names.size())),
                    Lang.unparsed("list", String.join(", ", names))));
            return;
        }
        Optional<UUID> target = Commands.findAny(core, source, args[0]);
        if (target.isEmpty()) {
            source.sendMessage(lang.get("player-not-found"));
            return;
        }
        String name = Commands.nameOf(core, target.get());
        if (target.get().equals(me)) {
            source.sendMessage(lang.get("ignore-self"));
            return;
        }
        if (ignore) {
            Optional<ChatPlayer> online = core.platform().players().get(target.get());
            // Si el objetivo esta en vanish no se avisa de que es staff: delataria que esta conectado.
            if (online.isPresent() && online.get().hasPermission(EXEMPT)
                    && !core.pm().hiddenFrom(self.get(), online.get())) {
                source.sendMessage(lang.get("ignore-exempt", Lang.unparsed("player", name)));
                return;
            }
            source.sendMessage(core.ignore(me, target.get())
                    ? lang.get("ignore-added", Lang.unparsed("player", name))
                    : lang.get("ignore-already", Lang.unparsed("player", name)));
        } else {
            source.sendMessage(core.unignore(me, target.get())
                    ? lang.get("ignore-removed", Lang.unparsed("player", name))
                    : lang.get("ignore-not-ignored", Lang.unparsed("player", name)));
        }
    }

    @Override
    public List<String> complete(CommandSource source, String[] args) {
        if (args.length != 1) return List.of();
        if (!ignore && source.player().isPresent()) {
            List<String> names = new ArrayList<>();
            for (UUID u : core.getIgnored(source.player().get().uuid())) names.add(Commands.nameOf(core, u));
            return Commands.filter(names, args[0]);
        }
        return Commands.onlineNames(core, source, args[0]);
    }
}
