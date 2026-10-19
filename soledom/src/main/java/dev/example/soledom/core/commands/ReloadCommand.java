package dev.example.soledom.core.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.SoledomPlugin;

/** {@code /soledom reload}: hot-reloads config, messages, models and animations. */
final class ReloadCommand implements SubCommand {

    private final SoledomPlugin plugin;

    ReloadCommand(@NotNull SoledomPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() {
        return "reload";
    }

    @Override
    public @NotNull String permission() {
        return "soledom.admin";
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        plugin.messages().send(sender, "reload.start");
        if (!plugin.reload(sender)) {
            plugin.messages().send(sender, "reload.busy");
        }
    }
}
