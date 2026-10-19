package dev.example.soledom.core.commands;

import java.util.List;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.SoledomPlugin;
import dev.example.soledom.core.config.Messages;
import dev.example.soledom.runtime.ModelEntityController;

/** {@code /soledom remove [target]}: removes the targeted (default: looked-at or nearest) model. */
final class RemoveCommand implements SubCommand {

    private final SoledomPlugin plugin;
    private final TargetResolver targets;

    RemoveCommand(@NotNull SoledomPlugin plugin, @NotNull TargetResolver targets) {
        this.plugin = plugin;
        this.targets = targets;
    }

    @Override
    public @NotNull String name() {
        return "remove";
    }

    @Override
    public @NotNull String permission() {
        return "soledom.spawn";
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        Optional<ModelEntityController> target = resolve(plugin, targets, sender, args, 0);
        if (target == null) {
            return;
        }
        if (target.isEmpty()) {
            plugin.messages().send(sender, "target.not-found");
            return;
        }
        ModelEntityController controller = target.get();
        controller.remove();
        plugin.messages().send(sender, "remove.success", Messages.ph("model", controller.getModel().id()),
                Messages.ph("uuid", controller.getUniqueId()));
    }

    @Override
    public @NotNull List<String> complete(@NotNull CommandSender sender, @NotNull String[] args) {
        return args.length == 1 ? TargetResolver.suggestions(plugin) : List.of();
    }

    /**
     * Shared target lookup: {@code args[index]} if present, else the default target for players.
     *
     * @return {@code null} if a message was already sent (console without target)
     */
    static Optional<ModelEntityController> resolve(SoledomPlugin plugin, TargetResolver targets, CommandSender sender,
            String[] args, int index) {
        if (args.length > index) {
            return targets.resolve(sender, args[index]);
        }
        if (sender instanceof Player player) {
            return targets.resolveDefault(player);
        }
        plugin.messages().send(sender, "target.console-needs-uuid");
        return null;
    }
}
