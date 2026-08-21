package dev.bookreports.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * Bridges the plugin's existing {@link CommandExecutor}/{@link TabCompleter} pairs to Paper's Brigadier-based
 * {@link BasicCommand} — paper-plugin.yml's YAML {@code commands:} block, and {@code JavaPlugin#getCommand}, are no
 * longer supported as of Paper 26.x; {@code getCommand} throws {@code UnsupportedOperationException} at startup
 * instead. See {@code BookReportsPlugin#registerCommands}.
 *
 * <p>
 * Paper fires the registration event this is registered against very early — well before storage connects and the real
 * executor (which usually needs {@code ReportService}) exists. Rather than delay registration (which risks missing that
 * one-shot event entirely), the adapter is registered immediately with no executor bound yet, and {@link #bind} swaps
 * the real one in once it's ready. A command run in that brief startup window gets a "still starting up" message
 * instead of an NPE.
 */
public final class BasicCommandAdapter implements BasicCommand {

    private final String label;
    private final String permission;
    private volatile CommandExecutor executor;

    public BasicCommandAdapter(String label, String permission) {
        this.label = Objects.requireNonNull(label, "label");
        this.permission = permission;
    }

    public void bind(CommandExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        CommandExecutor current = executor;
        CommandSender sender = commandSourceStack.getSender();
        if (current == null) {
            sender.sendMessage(
                    Component.text("BookReports is still starting up, try again in a moment.", NamedTextColor.RED));
            return;
        }
        current.onCommand(sender, null, label, args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
        if (!(executor instanceof TabCompleter tabCompleter)) {
            return List.of();
        }
        List<String> suggestions = tabCompleter.onTabComplete(commandSourceStack.getSender(), null, label, args);
        return suggestions != null ? suggestions : List.of();
    }

    @Override
    public String permission() {
        return permission;
    }
}
