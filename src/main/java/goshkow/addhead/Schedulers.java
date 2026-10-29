package goshkow.addhead;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Scheduling facade that works on both Paper/Spigot and Folia.
 *
 * <p>Paper and Spigot expose a single {@link org.bukkit.scheduler.BukkitScheduler},
 * while Folia replaces it with regionised schedulers: global region, async and
 * per-entity schedulers. Calls to {@code Bukkit.getScheduler()} throw
 * {@link UnsupportedOperationException} on Folia, which is why every scheduled
 * task in AddHeads goes through this class.</p>
 *
 * <p>Regression context: issue #7 reports AddHeads failing to enable on Folia
 * with {@code UnsupportedOperationException} coming from
 * {@code CraftScheduler.runTaskTimerAsynchronously}.</p>
 */
public final class Schedulers {

    /**
     * Handle of a scheduled task. Cancelling a finished task is a no-op.
     */
    public interface Handle {
        Handle NONE = () -> {
        };

        void cancel();
    }

    private static final boolean FOLIA = detectFolia();

    private Schedulers() {
    }

    /**
     * Returns whether the server runs on Folia (or a Folia fork).
     *
     * @return {@code true} when regionised schedulers are available
     */
    public static boolean isFolia() {
        return FOLIA;
    }

    /**
     * Runs a task on the global (main) thread. On Folia this is the global
     * region thread, which is the only place where global state may be touched.
     *
     * @param plugin owning plugin
     * @param task   task to run
     * @return cancellable handle
     */
    public static Handle runGlobal(Plugin plugin, Runnable task) {
        if (FOLIA) {
            return Folia.runGlobal(plugin, task);
        }
        return handle(Bukkit.getScheduler().runTask(plugin, task));
    }

    /**
     * Runs a task on the global thread after a delay measured in ticks.
     *
     * @param plugin     owning plugin
     * @param task       task to run
     * @param delayTicks delay in ticks (20 ticks = 1 second)
     * @return cancellable handle
     */
    public static Handle runGlobalLater(Plugin plugin, Runnable task, long delayTicks) {
        long delay = Math.max(0L, delayTicks);
        if (FOLIA) {
            return Folia.runGlobalLater(plugin, task, delay);
        }
        return handle(Bukkit.getScheduler().runTaskLater(plugin, task, delay));
    }

    /**
     * Repeats a task on the global thread.
     *
     * @param plugin      owning plugin
     * @param task        task to run
     * @param delayTicks  initial delay in ticks
     * @param periodTicks period in ticks
     * @return cancellable handle
     */
    public static Handle runGlobalTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        long delay = Math.max(0L, delayTicks);
        long period = Math.max(1L, periodTicks);
        if (FOLIA) {
            return Folia.runGlobalTimer(plugin, task, delay, period);
        }
        return handle(Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period));
    }

    /**
     * Runs a task on the global thread once the given entity can be ticked.
     * On Folia the entity scheduler keeps the work on the owning region thread.
     * Falls back to the global thread when the entity is not schedulable (for
     * example an offline player).
     *
     * @param plugin owning plugin
     * @param entity entity that owns the work
     * @param task   task to run
     * @return cancellable handle
     */
    public static Handle runFor(Plugin plugin, Entity entity, Runnable task) {
        if (FOLIA && entity != null && entity.getScheduler() != null) {
            return Folia.runFor(plugin, entity, task);
        }
        return runGlobal(plugin, task);
    }

    /**
     * Runs a task on the given entity's thread after a delay measured in ticks.
     *
     * @param plugin     owning plugin
     * @param entity     entity that owns the work
     * @param task       task to run
     * @param delayTicks delay in ticks
     * @return cancellable handle
     */
    public static Handle runForLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        long delay = Math.max(0L, delayTicks);
        if (FOLIA && entity != null && entity.getScheduler() != null) {
            return Folia.runForLater(plugin, entity, task, delay);
        }
        return runGlobalLater(plugin, task, delay);
    }

    /**
     * Runs a task off the global thread. Skin resolution, HTTP requests and
     * file downloads must not run on a ticking thread.
     *
     * @param plugin owning plugin
     * @param task   task to run
     * @return cancellable handle
     */
    public static Handle runAsync(Plugin plugin, Runnable task) {
        if (FOLIA) {
            return Folia.runAsync(plugin, task);
        }
        return handle(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }

    /**
     * Runs a task off the global thread after a tick-based delay. Folia has no
     * tick-based async delay, so ticks are converted to milliseconds.
     *
     * @param plugin     owning plugin
     * @param task       task to run
     * @param delayTicks delay in ticks
     * @return cancellable handle
     */
    public static Handle runAsyncLater(Plugin plugin, Runnable task, long delayTicks) {
        long delay = Math.max(0L, delayTicks);
        if (FOLIA) {
            return Folia.runAsyncLater(plugin, task, delay);
        }
        return handle(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay));
    }

    /**
     * Repeats a task off the global thread.
     *
     * @param plugin      owning plugin
     * @param task        task to run
     * @param delayTicks  initial delay in ticks
     * @param periodTicks period in ticks
     * @return cancellable handle
     */
    public static Handle runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        long delay = Math.max(0L, delayTicks);
        long period = Math.max(1L, periodTicks);
        if (FOLIA) {
            return Folia.runAsyncTimer(plugin, task, delay, period);
        }
        return handle(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period));
    }

    private static Handle handle(org.bukkit.scheduler.BukkitTask task) {
        return task == null ? Handle.NONE : task::cancel;
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * Folia implementation, isolated in a nested class so that servers without
     * the regionised scheduler API never resolve these classes.
     */
    private static final class Folia {

        private Folia() {
        }

        static Handle runGlobal(Plugin plugin, Runnable task) {
            return wrap(Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> task.run()));
        }

        static Handle runGlobalLater(Plugin plugin, Runnable task, long delayTicks) {
            return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> task.run(), delayTicks));
        }

        static Handle runGlobalTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
            return wrap(Bukkit.getGlobalRegionScheduler()
                    .runAtFixedRate(plugin, ignored -> task.run(), delayTicks, periodTicks));
        }

        static Handle runFor(Plugin plugin, Entity entity, Runnable task) {
            return wrap(entity.getScheduler().run(plugin, ignored -> task.run(), null));
        }

        static Handle runForLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
            return wrap(entity.getScheduler().runDelayed(plugin, ignored -> task.run(), null, delayTicks));
        }

        static Handle runAsync(Plugin plugin, Runnable task) {
            return wrap(Bukkit.getAsyncScheduler().runNow(plugin, ignored -> task.run()));
        }

        static Handle runAsyncLater(Plugin plugin, Runnable task, long delayTicks) {
            return wrap(Bukkit.getAsyncScheduler()
                    .runDelayed(plugin, ignored -> task.run(), toMillis(delayTicks), TimeUnit.MILLISECONDS));
        }

        static Handle runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
            return wrap(Bukkit.getAsyncScheduler().runAtFixedRate(
                    plugin,
                    ignored -> task.run(),
                    toMillis(delayTicks),
                    toMillis(periodTicks),
                    TimeUnit.MILLISECONDS
            ));
        }

        private static long toMillis(long ticks) {
            return Math.max(1L, ticks * 50L);
        }

        private static Handle wrap(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
            return task == null ? Handle.NONE : task::cancel;
        }
    }
}
