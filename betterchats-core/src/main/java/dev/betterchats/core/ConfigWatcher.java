package dev.betterchats.core;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Observador de archivos para desarrollo ({@code devAutoReload}): recarga al
 * guardar cualquier .json de la raiz o de chats/. Espera 1 s sin cambios antes
 * de recargar para no hacerlo a mitad de un guardado.
 */
final class ConfigWatcher implements AutoCloseable {

    private final Path folder;
    private final Logger logger;
    private final Runnable onChange;
    private WatchService service;
    private Thread thread;

    ConfigWatcher(Path folder, Logger logger, Runnable onChange) {
        this.folder = folder;
        this.logger = logger;
        this.onChange = onChange;
    }

    void start() {
        try {
            service = FileSystems.getDefault().newWatchService();
            folder.register(service, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
            folder.resolve("chats").register(service, StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "[BetterChats] No se pudo iniciar devAutoReload", ex);
            return;
        }
        thread = new Thread(this::loop, "BetterChats-ConfigWatcher");
        thread.setDaemon(true);
        thread.start();
        logger.info("[BetterChats] devAutoReload activo: se recarga al guardar los .json");
    }

    private void loop() {
        try {
            while (true) {
                WatchKey key = service.take();
                boolean relevant = key.pollEvents().stream()
                        .anyMatch(e -> e.context() != null && e.context().toString().endsWith(".json"));
                key.reset();
                if (!relevant) continue;
                // Debounce: absorbe los eventos del mismo guardado.
                WatchKey more;
                while ((more = service.poll(1, TimeUnit.SECONDS)) != null) {
                    more.pollEvents();
                    more.reset();
                }
                onChange.run();
            }
        } catch (InterruptedException | ClosedWatchServiceException ignored) {
            // cerrado
        }
    }

    @Override
    public void close() {
        try {
            if (service != null) service.close();
        } catch (IOException ignored) {
        }
        if (thread != null) thread.interrupt();
    }
}
