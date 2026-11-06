package dev.betterchats.legacy.hook;

import dev.betterchats.api.spy.SpyChannel;
import dev.betterchats.core.BetterChatsCore;
import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.team.TeamImpl;
import dev.betterchats.core.util.Texts;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Placeholders {@code %betterchats_...%}. Solo se carga si PlaceholderAPI esta
 * instalado (hereda de una clase suya).
 *
 * <ul>
 *   <li>{@code chat} - nombre visible del chat activo (con colores)</li>
 *   <li>{@code chat_id} - id del chat activo</li>
 *   <li>{@code spy} - true si tiene el spy activo en algun canal</li>
 *   <li>{@code spy_channels} - canales espiados, separados por coma</li>
 *   <li>{@code ignored_count} - cuantos jugadores ignora</li>
 *   <li>{@code last_partner} - ultimo interlocutor de /msg (vacio si no hay)</li>
 *   <li>{@code access_<chat>} - true si puede leer/escribir en ese chat</li>
 *   <li>{@code member_<chat>} - true si es miembro explicito de ese chat</li>
 * </ul>
 */
public final class BetterChatsExpansion extends PlaceholderExpansion {

    private final BetterChatsCore core;
    private final String version;

    public BetterChatsExpansion(BetterChatsCore core, String version) {
        this.core = core;
        this.version = version;
    }

    @Override
    public String getIdentifier() {
        return "betterchats";
    }

    @Override
    public String getAuthor() {
        return "Velez";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        UUID id = player.getUniqueId();
        String p = params.toLowerCase(Locale.ROOT);
        Optional<ChatPlayer> online = core.platform().players().get(id);

        switch (p) {
            case "chat":
                return Texts.legacy(core.renderTeamName(core.activeTeam(id)));
            case "chat_id":
                return core.activeTeam(id).getId();
            case "spy":
                return String.valueOf(online.map(cp -> !core.spy().activeFor(cp).isEmpty()).orElse(false));
            case "spy_channels": {
                if (online.isEmpty()) return "";
                List<String> ids = new ArrayList<>();
                for (SpyChannel ch : core.spy().activeFor(online.get())) ids.add(ch.getId());
                return String.join(", ", ids);
            }
            case "ignored_count":
                return online.isPresent() ? String.valueOf(core.getIgnored(id).size()) : "0";
            case "last_partner":
                return core.getLastConversationPartner(id)
                        .flatMap(u -> core.platform().players().lookupName(u)).orElse("");
            default:
                break;
        }
        if (p.startsWith("access_")) {
            Optional<TeamImpl> team = core.teams().get(p.substring("access_".length()));
            return String.valueOf(team.isPresent() && online.isPresent() && core.hasAccess(online.get(), team.get()));
        }
        if (p.startsWith("member_")) {
            Optional<TeamImpl> team = core.teams().get(p.substring("member_".length()));
            return String.valueOf(team.isPresent() && team.get().isMember(id));
        }
        return null;
    }
}
