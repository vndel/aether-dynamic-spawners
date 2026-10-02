package com.fivemcodehub.aether.command;

import com.fivemcodehub.aether.engine.SpawnerThrottleEngine;
import com.fivemcodehub.aether.engine.TickHealthMonitor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public final class AetherCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final TickHealthMonitor monitor;
    private final SpawnerThrottleEngine engine;

    public AetherCommand(JavaPlugin plugin, TickHealthMonitor monitor,
                         SpawnerThrottleEngine engine) {
        this.plugin = plugin;
        this.monitor = monitor;
        this.engine = engine;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(MM.deserialize("<green>Configuration reloaded.</green>"));
            return true;
        }

        sender.sendMessage(MM.deserialize("<gray>──── AetherDynamicSpawners ────</gray>"));
        sender.sendMessage(MM.deserialize(
                "<gray>MSPT (smoothed):</gray> <white><mspt></white>",
                Placeholder.unparsed("mspt", String.format("%.2f", monitor.smoothedMspt()))));
        sender.sendMessage(MM.deserialize("<gray>TPS:</gray> <white><tps></white>",
                Placeholder.unparsed("tps", String.format("%.2f", monitor.tps()))));
        sender.sendMessage(MM.deserialize("<gray>Headroom:</gray> <white><h>%</white>",
                Placeholder.unparsed("h", String.format("%.0f", monitor.headroom() * 100))));
        sender.sendMessage(MM.deserialize(
                "<gray>Delay multiplier:</gray> <yellow>x<m></yellow>",
                Placeholder.unparsed("m", String.format("%.2f", engine.multiplier()))));
        sender.sendMessage(MM.deserialize(
                "<gray>Adjusted last pass:</gray> <white><n></white>",
                Placeholder.unparsed("n", String.valueOf(engine.adjustedLastPass()))));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return args.length == 1
                ? List.of("status", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList()
                : List.of();
    }
}
