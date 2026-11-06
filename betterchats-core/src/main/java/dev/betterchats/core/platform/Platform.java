package dev.betterchats.core.platform;

import net.kyori.adventure.text.Component;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.logging.Logger;

/** Todo lo que el core necesita de la plataforma, agrupado. */
public interface Platform {

    String platformName();

    Logger logger();

    /** Carpeta de datos del plugin (plugins/BetterChats). */
    Path dataFolder();

    /** Recurso empaquetado en el jar (para copiar los archivos por defecto). */
    InputStream resource(String path);

    PlayerProvider players();

    Scheduler scheduler();

    SoundPlayer sounds();

    PlaceholderBridge placeholders();

    VanishBridge vanish();

    CommandRegistrar commands();

    void sendConsole(Component message);

    /**
     * Abre el menu de colores de chat (/chatcolor). Hilo principal.
     *
     * @return false si la plataforma no tiene menu (p. ej. falta PacketEvents)
     */
    default boolean openChatColorMenu(ChatPlayer player) {
        return false;
    }
}
