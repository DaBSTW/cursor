package dev.example.soledom.core.commands;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.SoledomPlugin;
import dev.example.soledom.core.config.Messages;
import dev.example.soledom.core.config.PackSettings;

/** {@code /soledom pack <export|send [player]|info>}. */
final class PackCommand implements SubCommand {

    private final SoledomPlugin plugin;

    PackCommand(@NotNull SoledomPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() {
        return "pack";
    }

    @Override
    public @NotNull String permission() {
        return "soledom.pack";
    }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "export" -> export(sender);
            case "send" -> send(sender, args);
            case "info" -> info(sender);
            default -> SoledomCommand.usage(plugin, sender, this);
        }
    }

    private void export(CommandSender sender) {
        plugin.messages().send(sender, "pack.start");
        plugin.packService().export().whenComplete((result, error) -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null
                        ? error.getCause() : error;
                plugin.messages().send(sender, cause instanceof IllegalStateException ? "pack.busy" : "pack.failed",
                        Messages.ph("error", String.valueOf(cause.getMessage())));
                return;
            }
            plugin.messages().send(sender, "pack.done", Messages.ph("path", result.zip()),
                    Messages.ph("sha1", result.sha1()), Messages.ph("size", formatSize(result.size())),
                    Messages.ph("entries", result.entries()));
            for (String warning : result.warnings()) {
                plugin.messages().send(sender, "pack.warning", Messages.ph("warning", warning));
            }
        });
    }

    private void send(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 1) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                plugin.messages().send(sender, "unknown-player", Messages.ph("player", args[1]));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (!plugin.packService().send(target)) {
            plugin.messages().send(sender, "pack.no-url");
            return;
        }
        plugin.messages().send(sender, "pack.sent", Messages.ph("player", target.getName()));
    }

    private void info(CommandSender sender) {
        PackSettings settings = plugin.settings().pack();
        String sha1 = plugin.packService().effectiveSha1(settings);
        plugin.messages().send(sender, "pack.info", Messages.ph("path", plugin.packService().zipPath()),
                Messages.ph("format", settings.packFormat()), Messages.ph("min", settings.minFormat()),
                Messages.ph("max", settings.maxFormat()), Messages.ph("url", settings.url().isBlank() ? "-"
                        : settings.url()), Messages.ph("sha1", sha1 == null ? "-" : sha1));
    }

    private static String formatSize(long bytes) {
        return bytes < 1024 ? bytes + " B" : String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
    }

    @Override
    public @NotNull List<String> complete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("export", "send", "info");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
