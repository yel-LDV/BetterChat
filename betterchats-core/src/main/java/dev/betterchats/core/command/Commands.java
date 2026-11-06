package dev.betterchats.core.command;

import dev.betterchats.core.BetterChatsCore;
import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.CommandSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Utilidades compartidas por los comandos. */
final class Commands {

    private Commands() {
    }

    static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    /** Jugador conectado por nombre exacto o, si es unico, por prefijo. Respeta vanish. */
    static Optional<ChatPlayer> findOnline(BetterChatsCore core, CommandSource source, String name) {
        Optional<ChatPlayer> exact = core.platform().players().getExact(name);
        if (exact.isPresent()) return exact;
        String lower = name.toLowerCase(Locale.ROOT);
        ChatPlayer match = null;
        for (ChatPlayer p : core.platform().players().online()) {
            if (!p.name().toLowerCase(Locale.ROOT).startsWith(lower)) continue;
            if (source.player().isPresent() && core.pm().hiddenFrom(source.player().get(), p)) continue;
            if (match != null) return Optional.empty();
            match = p;
        }
        return Optional.ofNullable(match);
    }

    /** UUID de un jugador conectado o conocido por el servidor. */
    static Optional<UUID> findAny(BetterChatsCore core, CommandSource source, String name) {
        Optional<ChatPlayer> online = findOnline(core, source, name);
        if (online.isPresent()) return Optional.of(online.get().uuid());
        return core.platform().players().lookupUuid(name);
    }

    static String nameOf(BetterChatsCore core, UUID uuid) {
        return core.platform().players().get(uuid).map(ChatPlayer::name)
                .or(() -> core.platform().players().lookupName(uuid))
                .orElse(uuid.toString().substring(0, 8));
    }

    /** Nombres conectados visibles para el origen, filtrados por prefijo. */
    static List<String> onlineNames(BetterChatsCore core, CommandSource source, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (ChatPlayer p : core.platform().players().online()) {
            if (source.player().isPresent() && core.pm().hiddenFrom(source.player().get(), p)) continue;
            if (p.name().toLowerCase(Locale.ROOT).startsWith(lower)) out.add(p.name());
        }
        return out;
    }

    static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(lower)) out.add(o);
        return out;
    }
}
