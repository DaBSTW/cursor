package dev.example.soledom.core.commands;

import java.util.List;
import java.util.concurrent.CompletionException;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.SoledomPlugin;
import dev.example.soledom.core.config.Messages;
import dev.example.soledom.core.utils.NamespacedIds;
import dev.example.soledom.runtime.SpawnException;

/**
 * {@code /soledom spawn <model>} at the player's position, or
 * {@code /soledom spawn <model> <world> <x> <y> <z> [yaw]} from the console.
 */
final class SpawnCommand implements SubCommand {

    private final SoledomPlugin plugin;

    SpawnCommand(@NotNull SoledomPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() {
        return "spawn";
    }

    @Override
    public @NotNull String permission() {
        return "soledom.spawn";
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0) {
            SoledomCommand.usage(plugin, sender, this);
            return;
        }
        String modelId = NamespacedIds.normalize(args[0], NamespacedIds.DEFAULT_NAMESPACE);
        if (modelId == null || !plugin.registry().contains(modelId)) {
            plugin.messages().send(sender, "spawn.unknown-model", Messages.ph("model", args[0]));
            return;
        }
        Location location = location(sender, args);
        if (location == null) {
            return;
        }
        plugin.manager().spawn(modelId, location, null).whenComplete((controller, error) -> {
            if (error == null) {
                plugin.messages().send(sender, "spawn.success", Messages.ph("model", modelId),
                        Messages.ph("uuid", controller.getUniqueId()));
                return;
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause()
                    : error;
            if (cause instanceof SpawnException spawn) {
                switch (spawn.getReason()) {
                    case CHUNK_LIMIT -> plugin.messages().send(sender, "spawn.chunk-limit",
                            Messages.ph("limit", plugin.settings().maxModelsPerChunk()));
                    case CANCELLED -> plugin.messages().send(sender, "spawn.cancelled");
                    case UNKNOWN_MODEL -> plugin.messages().send(sender, "spawn.unknown-model",
                            Messages.ph("model", modelId));
                    default -> plugin.messages().send(sender, "spawn.failed", Messages.ph("error", spawn.getMessage()));
                }
            } else {
                plugin.messages().send(sender, "spawn.failed", Messages.ph("error", String.valueOf(cause.getMessage())));
            }
        });
    }

    private Location location(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (sender instanceof Player player) {
                return player.getLocation();
            }
            plugin.messages().send(sender, "player-only");
            return null;
        }
        if (args.length < 5) {
            SoledomCommand.usage(plugin, sender, this);
            return null;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            plugin.messages().send(sender, "unknown-world", Messages.ph("world", args[1]));
            return null;
        }
        try {
            float yaw = args.length > 5 ? Float.parseFloat(args[5]) : 0f;
            return new Location(world, Double.parseDouble(args[2]), Double.parseDouble(args[3]),
                    Double.parseDouble(args[4]), yaw, 0f);
        } catch (NumberFormatException e) {
            plugin.messages().send(sender, "invalid-number", Messages.ph("input", String.join(" ", args)));
            return null;
        }
    }

    @Override
    public @NotNull List<String> complete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return plugin.registry().ids();
        }
        if (args.length == 2 && !(sender instanceof Player)) {
            return Bukkit.getWorlds().stream().map(World::getName).toList();
        }
        return List.of();
    }
}
