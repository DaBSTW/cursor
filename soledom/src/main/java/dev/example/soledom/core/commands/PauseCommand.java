package dev.example.soledom.core.commands;

import java.util.List;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.SoledomPlugin;
import dev.example.soledom.core.config.Messages;
import dev.example.soledom.runtime.ModelEntityController;

/** {@code /soledom pause [target]} and {@code /soledom resume [target]}. */
final class PauseCommand implements SubCommand {

    private final SoledomPlugin plugin;
    private final TargetResolver targets;
    private final boolean pause;

    PauseCommand(@NotNull SoledomPlugin plugin, @NotNull TargetResolver targets, boolean pause) {
        this.plugin = plugin;
        this.targets = targets;
        this.pause = pause;
    }

    @Override
    public @NotNull String name() {
        return pause ? "pause" : "resume";
    }

    @Override
    public @NotNull String permission() {
        return "soledom.animate";
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        Optional<ModelEntityController> target = RemoveCommand.resolve(plugin, targets, sender, args, 0);
        if (target == null) {
            return;
        }
        if (target.isEmpty()) {
            plugin.messages().send(sender, "target.not-found");
            return;
        }
        ModelEntityController controller = target.get();
        if (pause) {
            controller.pauseAnimations();
        } else {
            controller.resumeAnimations();
        }
        plugin.messages().send(sender, pause ? "animate.paused" : "animate.resumed",
                Messages.ph("model", controller.getModel().id()));
    }

    @Override
    public @NotNull List<String> complete(@NotNull CommandSender sender, @NotNull String[] args) {
        return args.length == 1 ? TargetResolver.suggestions(plugin) : List.of();
    }
}
