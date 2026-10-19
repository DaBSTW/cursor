package dev.example.soledom.core.commands;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.SoledomPlugin;
import dev.example.soledom.core.config.Messages;

/** {@code /soledom} dispatcher with permission-aware help and tab completion. */
public final class SoledomCommand implements TabExecutor {

    private final SoledomPlugin plugin;
    private final Map<String, SubCommand> commands = new LinkedHashMap<>();

    public SoledomCommand(@NotNull SoledomPlugin plugin) {
        this.plugin = plugin;
        TargetResolver targets = new TargetResolver(plugin);
        register(new HelpCommand(plugin, this));
        register(new ReloadCommand(plugin));
        register(new ListCommand(plugin));
        register(new SpawnCommand(plugin));
        register(new RemoveCommand(plugin, targets));
        register(new AnimateCommand(plugin, targets));
        register(new StopAnimationCommand(plugin, targets));
        register(new PauseCommand(plugin, targets, true));
        register(new PauseCommand(plugin, targets, false));
        register(new PackCommand(plugin));
        register(new DebugCommand(plugin, targets));
    }

    private void register(SubCommand command) {
        commands.put(command.name(), command);
    }

    @NotNull List<SubCommand> visibleTo(@NotNull CommandSender sender) {
        return commands.values().stream().filter(c -> sender.hasPermission(c.permission())).toList();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        String name = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        SubCommand sub = commands.get(name);
        if (sub == null) {
            plugin.messages().send(sender, "unknown-subcommand", Messages.ph("input", name));
            return true;
        }
        if (!sender.hasPermission(sub.permission())) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        try {
            sub.execute(sender, args.length == 0 ? args : Arrays.copyOfRange(args, 1, args.length));
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error running /soledom " + name, e);
            plugin.messages().send(sender, "internal-error");
        }
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0];
            return filter(visibleTo(sender).stream().map(SubCommand::name).toList(), prefix);
        }
        SubCommand sub = commands.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null || !sender.hasPermission(sub.permission())) {
            return List.of();
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        return filter(sub.complete(sender, rest), rest[rest.length - 1]);
    }

    static @NotNull List<String> filter(@NotNull List<String> options, @NotNull String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)
                || (o.contains(":") && o.substring(o.indexOf(':') + 1).startsWith(lower))).distinct().toList();
    }

    /** Sends {@code usage.<key>} for a subcommand. */
    static void usage(@NotNull SoledomPlugin plugin, @NotNull CommandSender sender, @NotNull SubCommand command) {
        plugin.messages().send(sender, "usage", Messages.ph("usage", plugin.messages().raw("usage-text."
                + command.usageKey())));
    }
}
