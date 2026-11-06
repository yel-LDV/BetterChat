package dev.betterchats.core.platform;

/**
 * Planificador abstracto. En Bukkit "por jugador" es el hilo principal; en
 * Folia seria el scheduler de la entidad.
 */
public interface Scheduler {

    void runSync(Runnable task);

    void runAsync(Runnable task);

    /** Ejecuta en el hilo que puede tocar a este jugador (sonidos, etc.). */
    void runForPlayer(ChatPlayer player, Runnable task);

    TaskHandle runRepeating(Runnable task, long delayTicks, long periodTicks, boolean async);

    /** true si el hilo actual es el principal del servidor. */
    boolean isPrimaryThread();

    interface TaskHandle {
        void cancel();
    }
}
