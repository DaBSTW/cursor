package dev.example.soledom.core.commands;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/** One {@code /soledom <name>} subcommand. */
public interface SubCommand {

    @NotNull String name();

    /** Permission node required to run and see this subcommand. */
    @NotNull String permission();

    /** Key under {@code usage.} in messages.yml. */
    default @NotNull String usageKey() {
        return name();
    }

    /** @param args arguments after the subcommand name */
    void execute(@NotNull CommandSender sender, @NotNull String[] args);

    default @NotNull List<String> complete(@NotNull CommandSender sender, @NotNull String[] args) {
        return List.of();
    }
}
