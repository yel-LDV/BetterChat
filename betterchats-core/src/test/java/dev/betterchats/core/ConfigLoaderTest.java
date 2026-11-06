package dev.betterchats.core;

import dev.betterchats.core.config.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    private ConfigLoader loader(Path dir) {
        return new ConfigLoader(dir, p -> ConfigLoaderTest.class.getClassLoader().getResourceAsStream(p), key -> null);
    }

    @Test
    void porDefectoCargaSinErrores(@TempDir Path dir) {
        ConfigLoader.Result r = loader(dir).load();
        assertTrue(r.success(), () -> "errores: " + r.errors());
        assertEquals(2, r.bundle().teams().size());
        assertTrue(Files.exists(dir.resolve("chats/staff.json")));
        assertTrue(Files.exists(dir.resolve("private_messages.json")));
        assertTrue(Files.exists(dir.resolve("spy.json")));
    }

    @Test
    void errorConRutaDelCampo(@TempDir Path dir) throws IOException {
        loader(dir).load();
        Path staff = dir.resolve("chats/staff.json");
        Files.writeString(staff, Files.readString(staff).replace("\"pitch\": 1.5", "\"pitch\": 3.0"));
        ConfigLoader.Result r = loader(dir).load();
        assertFalse(r.success());
        assertTrue(r.errors().stream().anyMatch(e -> e.startsWith("chats/staff.json -> sound.pitch: debe estar entre 0.5 y 2.0")),
                () -> r.errors().toString());
    }

    @Test
    void exactamenteUnDefaultEIdCoincidente(@TempDir Path dir) throws IOException {
        loader(dir).load();
        Files.writeString(dir.resolve("chats/otro.json"), """
                { "id": "distinto", "isDefault": true, "format": "{message}" }
                """, StandardCharsets.UTF_8);
        ConfigLoader.Result r = loader(dir).load();
        assertFalse(r.success());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("debe coincidir con el nombre del archivo")), r.errors()::toString);
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("debe haber exactamente uno")), r.errors()::toString);
    }

    @Test
    void jsonMalFormadoYFaltaMessage(@TempDir Path dir) throws IOException {
        loader(dir).load();
        Files.writeString(dir.resolve("chats/vip.json"), "{ \"id\": \"vip\", \"format\": \"<gold>{playerName}\" }");
        Files.writeString(dir.resolve("private_messages.json"), "{ \"enabled\": true, ");
        ConfigLoader.Result r = loader(dir).load();
        assertFalse(r.success());
        assertTrue(r.errors().stream().anyMatch(e -> e.startsWith("chats/vip.json -> format: la plantilla debe incluir {message}")), r.errors()::toString);
        assertTrue(r.errors().stream().anyMatch(e -> e.startsWith("private_messages.json -> JSON mal formado")), r.errors()::toString);
    }

    @Test
    void chatColorsValidaColoresYSlots(@TempDir Path dir) throws IOException {
        ConfigLoader.Result ok = loader(dir).load();
        assertTrue(ok.success(), ok.errors()::toString);
        assertEquals(19, ok.bundle().chatColors().colors().size());
        assertEquals(5, ok.bundle().chatColors().formats().size());

        Path file = dir.resolve("chat_colors.json");
        Files.writeString(file, Files.readString(file)
                .replace("\"color\": \"red\"", "\"color\": \"rojito\"")
                .replace("\"slot\": 29", "\"slot\": 28"), StandardCharsets.UTF_8);
        ConfigLoader.Result r = loader(dir).load();
        assertFalse(r.success());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("chat_colors.json -> colors[12].color")), r.errors()::toString);
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("el slot 28 ya lo usa otro icono")), r.errors()::toString);
    }

    @Test
    void placeholdersObsoletosSeAceptanConAviso(@TempDir Path dir) throws IOException {
        loader(dir).load();
        Path pm = dir.resolve("private_messages.json");
        Files.writeString(pm, Files.readString(pm).replace("Tú a {receiver}", "Tú a {target}"));
        ConfigLoader.Result r = loader(dir).load();
        assertTrue(r.success(), r.errors()::toString);
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("{target} esta obsoleto")));
        assertTrue(r.bundle().privateMessages().formatOutgoing().contains("{receiver}"));
    }

    @Test
    void migraVersionAnteriorConCopia(@TempDir Path dir) throws IOException {
        loader(dir).load();
        Path cfg = dir.resolve("config.json");
        Files.writeString(cfg, "{ \"language\": \"es\", \"chat\": { \"strategy\": \"CANCEL\" } }");
        ConfigLoader.Result r = loader(dir).load();
        assertTrue(r.success(), r.errors()::toString);
        assertTrue(Files.readString(cfg).contains("\"configVersion\": 1"));
        assertTrue(Files.readString(cfg).contains("\"CANCEL\""), "conserva lo del usuario");
        try (var s = Files.list(dir.resolve("backups"))) {
            assertEquals(1, s.count());
        }
    }
}
