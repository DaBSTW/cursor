package dev.example.soledom.core.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.SoledomPlugin;
import dev.example.soledom.core.config.Messages;

/** {@code /soledom help}: lists the subcommands the sender may use. */
final class HelpCommand implements SubCommand {

    private final SoledomPlugin plugin;
    private final SoledomCommand root;

    HelpCommand(@NotNull SoledomPlugin plugin, @NotNull SoledomCommand root) {
        this.plugin = plugin;
        this.root = root;
    }

    @Override
    public @NotNull String name() {
        return "help";
    }

    @Override
    public @NotNull String permission() {
        return "soledom.use";
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        plugin.messages().send(sender, "help.header", Messages.ph("version", plugin.getPluginMeta().getVersion()));
        for (SubCommand command : root.visibleTo(sender)) {
            plugin.messages().send(sender, "help.line",
                    Messages.ph("usage", plugin.messages().raw("usage-text." + command.usageKey())),
                    Messages.ph("description", plugin.messages().raw("help.descriptions." + command.name())));
        }
    }
}
