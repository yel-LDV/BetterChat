package dev.betterchats.legacy;

import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Scheduler de Bukkit. "Por jugador" = hilo principal (en Folia seria el de la entidad). */
final class BukkitSchedulerAdapter implements Scheduler {

    private final Plugin plugin;

    BukkitSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runSync(Runnable task) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runAsync(Runnable task) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void runForPlayer(ChatPlayer player, Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else runSync(task);
    }

    @Override
    public TaskHandle runRepeating(Runnable task, long delayTicks, long periodTicks, boolean async) {
        BukkitTask t = async
                ? Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks)
                : Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return t::cancel;
    }

    @Override
    public boolean isPrimaryThread() {
        return Bukkit.isPrimaryThread();
    }
}
