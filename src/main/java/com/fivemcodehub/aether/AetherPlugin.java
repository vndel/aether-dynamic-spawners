package com.fivemcodehub.aether;

import com.fivemcodehub.aether.command.AetherCommand;
import com.fivemcodehub.aether.engine.SpawnerThrottleEngine;
import com.fivemcodehub.aether.engine.TickHealthMonitor;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Adaptive spawner throttling.
 *
 * <p>A dense spawner farm is one of the few player-built structures that can
 * saturate the tick loop: each spawner performs spawn-condition checks and
 * each spawned entity then carries AI and collision cost. Rather than imposing
 * a fixed cap, this measures tick headroom and reduces spawner activity only
 * while the server is actually struggling.
 */
public final class AetherPlugin extends JavaPlugin {

    private TickHealthMonitor monitor;
    private SpawnerThrottleEngine engine;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.monitor = new TickHealthMonitor(this);
        this.monitor.start();

        this.engine = new SpawnerThrottleEngine(this, monitor);
        this.engine.start();

        var cmd = getCommand("aether");
        if (cmd != null) {
            AetherCommand executor = new AetherCommand(this, monitor, engine);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("AetherDynamicSpawners enabled (target MSPT "
                + getConfig().getDouble("throttle.target-mspt", 35.0) + ")");
    }

    @Override
    public void onDisable() {
        if (engine != null) engine.shutdown();
        if (monitor != null) monitor.shutdown();
    }
}
