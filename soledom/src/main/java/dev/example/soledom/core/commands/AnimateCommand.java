package dev.example.soledom.core.commands;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.SoledomPlugin;
import dev.example.soledom.core.config.Messages;
import dev.example.soledom.core.utils.NamespacedIds;
import dev.example.soledom.registry.RegisteredModel;
import dev.example.soledom.runtime.ModelEntityController;

/** {@code /soledom animate <target> <animation> [speed]}. */
final class AnimateCommand implements SubCommand {

    private final SoledomPlugin plugin;
    private final TargetResolver targets;

    AnimateCommand(@NotNull SoledomPlugin plugin, @NotNull TargetResolver targets) {
        this.plugin = plugin;
        this.targets = targets;
    }

    @Override
    public @NotNull String name() {
        return "animate";
    }

    @Override
    public @NotNull String permission() {
        return "soledom.animate";
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            SoledomCommand.usage(plugin, sender, this);
            return;
        }
        double speed = 1.0;
        if (args.length > 2) {
            try {
                speed = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                speed = -1;
            }
            if (!(speed > 0 && speed <= 100)) {
                plugin.messages().send(sender, "invalid-number", Messages.ph("input", args[2]));
                return;
            }
        }
        Optional<ModelEntityController> target = targets.resolve(sender, args[0]);
        if (target.isEmpty()) {
            plugin.messages().send(sender, "target.not-found");
            return;
        }
        ModelEntityController controller = target.get();
        if (!controller.playAnimation(args[1], speed)) {
            RegisteredModel model = controller.registeredModel();
            String available = model.animations().isEmpty() ? "-"
                    : String.join(", ", new TreeSet<>(model.animations().keySet()));
            plugin.messages().send(sender, "animate.unknown", Messages.ph("animation", args[1]),
                    Messages.ph("model", model.id()), Messages.ph("available", available));
            return;
        }
        plugin.messages().send(sender, "animate.started", Messages.ph("animation", args[1]),
                Messages.ph("model", controller.getModel().id()));
    }

    @Override
    public @NotNull List<String> complete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return TargetResolver.suggestions(plugin);
        }
        if (args.length == 2) {
            return animationNames(plugin, targets, sender, args[0]);
        }
        return args.length == 3 ? List.of("0.5", "1", "2") : List.of();
    }

    /** Animation names of the model a target token refers to, or of every model when it can't be resolved. */
    static List<String> animationNames(SoledomPlugin plugin, TargetResolver targets, CommandSender sender,
            String token) {
        String modelId = NamespacedIds.normalize(token, NamespacedIds.DEFAULT_NAMESPACE);
        Optional<RegisteredModel> model = modelId == null ? Optional.empty() : plugin.registry().get(modelId);
        if (model.isEmpty()) {
            try {
                model = targets.resolve(sender, token).map(ModelEntityController::registeredModel);
            } catch (RuntimeException ignored) {
                model = Optional.empty();
            }
        }
        if (model.isPresent()) {
            return model.get().animations().keySet().stream().sorted().toList();
        }
        TreeSet<String> all = new TreeSet<>();
        plugin.registry().all().forEach(m -> all.addAll(m.animations().keySet()));
        return List.copyOf(all);
    }
}
