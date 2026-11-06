package dev.betterchats.core.format;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Decide que etiquetas MiniMessage puede usar un jugador en su mensaje
 * (seccion 6 del plan):
 *
 * <ol>
 *   <li>Lista del chat ({@code allowedPlayerTags}) + lista global
 *       ({@code security.defaultAllowedPlayerTags}), sin las protegidas.</li>
 *   <li>Permisos de grupo: {@code betterchats.tags.color}, {@code betterchats.tags.format}.</li>
 *   <li>Permiso por etiqueta: {@code betterchats.tags.<etiqueta>} (unica via, junto con
 *       el bypass, para click, hover, insertion, font, keybind...).</li>
 *   <li>Bypass ({@code betterchats.tags.all} por defecto): todas.</li>
 * </ol>
 */
public final class TagPolicy {

    public static final String PERMISSION_PREFIX = "betterchats.tags.";

    private TagPolicy() {
    }

    public static Set<String> allowedTags(Set<String> teamTags, Set<String> defaultTags, String bypassPermission,
                                          Predicate<String> hasPermission) {
        if (bypassPermission != null && hasPermission.test(bypassPermission)) {
            return Set.copyOf(TagCatalog.RESOLVERS.keySet());
        }
        Set<String> out = new HashSet<>();
        addExpanded(out, teamTags);
        addExpanded(out, defaultTags);
        out.removeAll(TagCatalog.PROTECTED);

        for (var group : TagCatalog.GROUPS.entrySet()) {
            if (hasPermission.test(PERMISSION_PREFIX + group.getKey())) out.addAll(group.getValue());
        }
        for (String tag : TagCatalog.ORDER) {
            if (!out.contains(tag) && hasPermission.test(PERMISSION_PREFIX + tag)) out.add(tag);
        }
        return Set.copyOf(out);
    }

    private static void addExpanded(Set<String> out, Set<String> tags) {
        for (String t : tags) {
            String n = TagCatalog.normalize(t);
            Set<String> group = TagCatalog.GROUPS.get(n);
            if (group != null) out.addAll(group);
            else if (TagCatalog.RESOLVERS.containsKey(n)) out.add(n);
        }
    }
}
