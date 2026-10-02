package com.fivemcodehub.aether.engine;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Applies a delay multiplier to loaded spawners based on tick headroom.
 *
 * <p>Implementation detail that matters: spawner state is only read and
 * written for chunks already loaded, and the scan is spread across ticks. A
 * naive "iterate every chunk every second" pass would itself become the lag
 * source it is meant to prevent.
 */
public final class SpawnerThrottleEngine {

    private final JavaPlugin plugin;
    private final TickHealthMonitor monitor;

    private final AtomicInteger adjustedLastPass = new AtomicInteger();
    private volatile double currentMultiplier = 1.0;

    private BukkitTask task;

    public SpawnerThrottleEngine(JavaPlugin plugin, TickHealthMonitor monitor) {
        this.plugin = plugin;
        this.monitor = monitor;
    }

    public void start() {
        long interval = Math.max(20L, plugin.getConfig()
                .getLong("throttle.evaluate-interval-ticks", 100L));
        this.task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::evaluate, interval, interval);
    }

    private void evaluate() {
        double target = plugin.getConfig().getDouble("throttle.target-mspt", 35.0);
        double mspt = monitor.smoothedMspt();

        double desired;
        if (mspt <= target) {
            // Under budget: ease back toward vanilla rates instead of snapping,
            // so the throttle does not flap around the threshold.
            desired = Math.max(1.0, currentMultiplier - 0.25);
        } else {
            double overload = (mspt - target) / target;
            double maximum = plugin.getConfig().getDouble("throttle.max-multiplier", 4.0);
            desired = Math.min(maximum, 1.0 + overload * 3.0);
        }

        if (Math.abs(desired - currentMultiplier) < 0.05) return;
        currentMultiplier = desired;

        applyMultiplier(desired);
    }

    private void applyMultiplier(double multiplier) {
        int baseDelay = plugin.getConfig().getInt("throttle.base-min-spawn-delay", 200);
        int baseMax = plugin.getConfig().getInt("throttle.base-max-spawn-delay", 800);
        int perPass = plugin.getConfig().getInt("throttle.max-spawners-per-pass", 400);

        int adjusted = 0;
        outer:
        for (World world : plugin.getServer().getWorlds()) {
            // Only loaded chunks: forcing a load here would defeat the purpose.
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities(false)) {
                    if (!(state instanceof CreatureSpawner spawner)) continue;

                    int min = (int) Math.round(baseDelay * multiplier);
                    int max = (int) Math.round(baseMax * multiplier);

                    if (spawner.getMinSpawnDelay() == min && spawner.getMaxSpawnDelay() == max) {
                        continue;
                    }

                    spawner.setMinSpawnDelay(min);
                    spawner.setMaxSpawnDelay(max);
                    spawner.update(false, false);

                    if (++adjusted >= perPass) break outer;
                }
            }
        }

        adjustedLastPass.set(adjusted);

        if (adjusted > 0 && plugin.getConfig().getBoolean("throttle.log-adjustments", true)) {
            plugin.getLogger().info(String.format(
                    "Throttle x%.2f applied to %d spawner(s) (MSPT %.1f, headroom %.0f%%)",
                    multiplier, adjusted, monitor.smoothedMspt(), monitor.headroom() * 100));
        }
    }

    public double multiplier() {
        return currentMultiplier;
    }

    public int adjustedLastPass() {
        return adjustedLastPass.get();
    }

    public void shutdown() {
        if (task != null) task.cancel();
        // Restore vanilla timings so the world is not left throttled.
        applyMultiplier(1.0);
    }
}
