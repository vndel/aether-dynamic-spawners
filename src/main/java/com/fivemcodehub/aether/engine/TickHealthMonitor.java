package com.fivemcodehub.aether.engine;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Rolling MSPT/TPS sampler.
 *
 * <p>Raw {@code getAverageTickTime()} is noisy enough that acting on a single
 * reading causes the throttle to oscillate. A short rolling window smooths
 * transients such as a chunk-save burst while still reacting within seconds to
 * a genuine load increase.
 */
public final class TickHealthMonitor {

    private final JavaPlugin plugin;
    private final double[] window;

    private int cursor;
    private int filled;
    private BukkitTask task;

    public TickHealthMonitor(JavaPlugin plugin) {
        this.plugin = plugin;
        int size = Math.max(4, plugin.getConfig().getInt("throttle.sample-window", 20));
        this.window = new double[size];
    }

    public void start() {
        // Sampling must run on the main thread to observe real tick timing.
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            window[cursor] = plugin.getServer().getAverageTickTime();
            cursor = (cursor + 1) % window.length;
            if (filled < window.length) filled++;
        }, 20L, 20L);
    }

    public double smoothedMspt() {
        if (filled == 0) return plugin.getServer().getAverageTickTime();
        double sum = 0;
        for (int i = 0; i < filled; i++) sum += window[i];
        return sum / filled;
    }

    public double tps() {
        double[] tps = plugin.getServer().getTPS();
        return tps.length > 0 ? tps[0] : 20.0;
    }

    /**
     * Fraction of the 50ms tick budget still unused, in [0,1].
     * 1.0 means idle; 0.0 means the tick budget is fully consumed.
     */
    public double headroom() {
        double mspt = smoothedMspt();
        return Math.max(0.0, Math.min(1.0, (50.0 - mspt) / 50.0));
    }

    public void shutdown() {
        if (task != null) task.cancel();
    }
}
