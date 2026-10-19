package dev.example.soledom.core.commands;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.SoledomPlugin;
import dev.example.soledom.core.config.Messages;
import dev.example.soledom.registry.RegisteredModel;

/** {@code /soledom list}: registered models with their parts and animations. */
final class ListCommand implements SubCommand {

    private final SoledomPlugin plugin;

    ListCommand(@NotNull SoledomPlugin plugin) {
        this.plugin = plugin;
    }

    private static String source(RegisteredModel model) {
        if (model.external()) {
            return "api";
        }
        return model.definition().metadata().getOrDefault("source", "file").startsWith("blockbench:")
                ? "blockbench" : "file";
    }

    @Override
    public @NotNull String name() {
        return "list";
    }

    @Override
    public @NotNull String permission() {
        return "soledom.use";
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        List<RegisteredModel> models = plugin.modelsSorted();
        if (models.isEmpty()) {
            plugin.messages().send(sender, "list.empty");
            return;
        }
        plugin.messages().send(sender, "list.header", Messages.ph("count", models.size()),
                Messages.ph("active", plugin.manager().activeCount()));
        for (RegisteredModel model : models) {
            String animations = model.animations().isEmpty() ? "-" : String.join(", ",
                    model.animations().keySet().stream().sorted().toList());
            plugin.messages().send(sender, "list.entry", Messages.ph("id", model.id()),
                    Messages.ph("parts", model.definition().parts().size()), Messages.ph("animations", animations),
                    Messages.ph("source", source(model)));
        }
    }
}
