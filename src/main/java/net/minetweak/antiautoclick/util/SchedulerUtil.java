/*
 * AntiAutoClicker - Paper Plugin to detect automated clicking
 * Copyright (c) 2026
 */
package net.minetweak.antiautoclick.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

/**
 * Scheduler utility that abstracts Paper and Folia scheduling differences.
 * Automatically detects Folia and uses the appropriate scheduler.
 */
public final class SchedulerUtil {
    
    private static final boolean IS_FOLIA;
    
    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        IS_FOLIA = folia;
    }
    
    private SchedulerUtil() {}
    
    /**
     * Check if we're running on Folia
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }
    
    /**
     * Run a task on the entity's region thread (Folia) or main thread (Paper)
     */
    public static void runTask(Plugin plugin, Entity entity, Runnable task) {
        if (IS_FOLIA) {
            entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    /**
     * Run a task later on the entity's region thread (Folia) or main thread (Paper)
     */
    public static void runTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }
    
    /**
     * Run a task on the global region scheduler (Folia) or main thread (Paper)
     * Use this for tasks not tied to a specific entity/location
     */
    public static void runGlobalTask(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    /**
     * Run a task later on the global region scheduler (Folia) or main thread (Paper)
     */
    public static void runGlobalTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }
    
    /**
     * Run a repeating task on the global region scheduler (Folia) or main thread (Paper)
     */
    public static void runGlobalTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delayTicks > 0 ? delayTicks : 1, periodTicks);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }
    
    /**
     * Run a task asynchronously
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }
    
    /**
     * Run a task asynchronously after a delay
     */
    public static void runAsyncLater(Plugin plugin, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            // Folia async scheduler uses real time, convert ticks to milliseconds
            long delayMs = delayTicks * 50L;
            Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayMs, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }
    
    /**
     * Wrapper class to handle cancellable tasks across Paper/Folia
     */
    public static class ScheduledTask {
        private final BukkitTask bukkitTask;
        private final io.papermc.paper.threadedregions.scheduler.ScheduledTask foliaTask;
        
        public ScheduledTask(BukkitTask bukkitTask) {
            this.bukkitTask = bukkitTask;
            this.foliaTask = null;
        }
        
        public ScheduledTask(io.papermc.paper.threadedregions.scheduler.ScheduledTask foliaTask) {
            this.bukkitTask = null;
            this.foliaTask = foliaTask;
        }
        
        public void cancel() {
            if (bukkitTask != null) {
                bukkitTask.cancel();
            }
            if (foliaTask != null) {
                foliaTask.cancel();
            }
        }
        
        public boolean isCancelled() {
            if (bukkitTask != null) {
                return bukkitTask.isCancelled();
            }
            if (foliaTask != null) {
                return foliaTask.isCancelled();
            }
            return true;
        }
    }
    
    /**
     * Schedule a delayed task for an entity that returns a cancellable task
     */
    public static ScheduledTask scheduleEntityTask(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask foliaTask = 
                entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
            return new ScheduledTask(foliaTask);
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return new ScheduledTask(bukkitTask);
        }
    }
}
