package dev.example.soledom.core.commands;

import java.util.List;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.SoledomPlugin;
import dev.example.soledom.core.config.Messages;
import dev.example.soledom.runtime.ModelEntityController;

/** {@code /soledom stopanimation [target] [animation]}: stops one or every animation. */
final class StopAnimationCommand implements SubCommand {

    private final SoledomPlugin plugin;
    private final TargetResolver targets;

    StopAnimationCommand(@NotNull SoledomPlugin plugin, @NotNull TargetResolver targets) {
        this.plugin = plugin;
        this.targets = targets;
    }

    @Override
    public @NotNull String name() {
        return "stopanimation";
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
        if (args.length > 1) {
            controller.stopAnimation(args[1]);
        } else {
            controller.stopAllAnimations();
        }
        plugin.messages().send(sender, "animate.stopped", Messages.ph("model", controller.getModel().id()),
                Messages.ph("animation", args.length > 1 ? args[1] : "*"));
    }

    @Override
    public @NotNull List<String> complete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return TargetResolver.suggestions(plugin);
        }
        return args.length == 2 ? AnimateCommand.animationNames(plugin, targets, sender, args[0]) : List.of();
    }
}
