package dev.example.soledom.core.commands;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.SoledomPlugin;
import dev.example.soledom.compat.Capability;
import dev.example.soledom.core.config.Messages;
import dev.example.soledom.loader.ContentLoader;
import dev.example.soledom.runtime.DisplayPart;
import dev.example.soledom.runtime.ModelEntityController;

/** {@code /soledom debug [info|toggle|model [target]|cleanup [radius]]}. */
final class DebugCommand implements SubCommand {

    private final SoledomPlugin plugin;
    private final TargetResolver targets;

    DebugCommand(@NotNull SoledomPlugin plugin, @NotNull TargetResolver targets) {
        this.plugin = plugin;
        this.targets = targets;
    }

    @Override
    public @NotNull String name() {
        return "debug";
    }

    @Override
    public @NotNull String permission() {
        return "soledom.admin";
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        String action = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "info" -> info(sender);
            case "toggle" -> {
                boolean enabled = !plugin.settings().debug();
                plugin.setDebug(enabled);
                plugin.messages().send(sender, enabled ? "debug.enabled" : "debug.disabled");
            }
            case "model" -> model(sender, args);
            case "cleanup" -> cleanup(sender, args);
            default -> SoledomCommand.usage(plugin, sender, this);
        }
    }

    private void info(CommandSender sender) {
        ContentLoader.LoadReport report = plugin.lastReport();
        plugin.messages().send(sender, "debug.info",
                Messages.ph("version", plugin.serverVersion()),
                Messages.ph("scheduler", plugin.features().hasFolia() ? "Folia (regionised)" : "Bukkit (main thread)"),
                Messages.ph("models", plugin.registry().size()),
                Messages.ph("active", plugin.manager().activeCount()),
                Messages.ph("animating", plugin.manager().all().stream().filter(c -> !c.players().isEmpty()).count()),
                Messages.ph("errors", report == null ? 0 : report.errors().size()),
                Messages.ph("warnings", report == null ? 0 : report.warnings().size()));
        for (Capability capability : Capability.values()) {
            plugin.messages().send(sender, plugin.features().has(capability) ? "debug.capability-on"
                    : "debug.capability-off", Messages.ph("capability", capability.name()));
        }
    }

    private void model(CommandSender sender, String[] args) {
        Optional<ModelEntityController> target = RemoveCommand.resolve(plugin, targets, sender, args, 1);
        if (target == null) {
            return;
        }
        if (target.isEmpty()) {
            plugin.messages().send(sender, "target.not-found");
            return;
        }
        ModelEntityController c = target.get();
        String animations = c.getPlayingAnimations().isEmpty() ? "-" : String.join(", ", c.getPlayingAnimations());
        plugin.messages().send(sender, "debug.model", Messages.ph("model", c.getModel().id()),
                Messages.ph("uuid", c.getUniqueId()), Messages.ph("state", c.state()),
                Messages.ph("parts", c.parts().size()), Messages.ph("hitbox", c.hitbox().isPresent()
                        ? c.hitbox().width() + "x" + c.hitbox().height() : "-"),
                Messages.ph("animations", animations), Messages.ph("visible", c.isVisible()));
        for (DisplayPart part : c.partsById().values()) {
            plugin.messages().send(sender, "debug.part", Messages.ph("part", part.id()),
                    Messages.ph("type", part.definition().type()), Messages.ph("valid", part.isValid()));
        }
    }

    /** Removes Soledom entities near the player that no live controller owns (e.g. after a crash). */
    private void cleanup(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        double radius = 32;
        if (args.length > 1) {
            try {
                radius = Math.min(256, Math.max(1, Double.parseDouble(args[1])));
            } catch (NumberFormatException e) {
                plugin.messages().send(sender, "invalid-number", Messages.ph("input", args[1]));
                return;
            }
        }
        int removed = 0;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            boolean orphan;
            if (plugin.keys().isPart(entity)) {
                UUID owner = plugin.keys().owner(entity);
                orphan = owner == null || plugin.manager().get(owner).isEmpty();
            } else if (plugin.keys().isRoot(entity)) {
                orphan = plugin.manager().find(entity).isEmpty();
            } else {
                continue;
            }
            if (orphan) {
                entity.remove();
                removed++;
            }
        }
        plugin.messages().send(sender, "debug.cleanup", Messages.ph("count", removed),
                Messages.ph("radius", (int) radius));
    }

    @Override
    public @NotNull List<String> complete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("info", "toggle", "model", "cleanup");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("model")) {
            return TargetResolver.suggestions(plugin);
        }
        return args.length == 2 && args[0].equalsIgnoreCase("cleanup") ? List.of("16", "32", "64") : List.of();
    }
}
