package dev.pregen.command;

import static dev.pregen.command.CommandSupport.sender;
import static dev.pregen.command.CommandSupport.success;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.pregen.diagnostics.Doctor;
import dev.pregen.i18n.MessageKey;
import dev.pregen.progress.BossBarDisplay;
import dev.pregen.trim.TrimService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import org.bukkit.command.CommandSender;

/**
 * Builds the {@code /pregen} Brigadier tree. Each subcommand declares its permission with {@code requires}, so the
 * client only sees and completes what the sender may run.
 */
public final class PregenCommand {

    public static final String NAME = "pregen";
    public static final List<String> ALIASES = List.of("pg");

    private record HelpLine(String permission, MessageKey key) {
    }

    private static final List<HelpLine> HELP = List.of(new HelpLine("start", MessageKey.HELP_START),
            new HelpLine("pause", MessageKey.HELP_PAUSE), new HelpLine("resume", MessageKey.HELP_RESUME),
            new HelpLine("cancel", MessageKey.HELP_CANCEL), new HelpLine("confirm", MessageKey.HELP_CONFIRM),
            new HelpLine("status", MessageKey.HELP_STATUS), new HelpLine("watch", MessageKey.HELP_WATCH),
            new HelpLine("selection", MessageKey.HELP_SELECTION), new HelpLine("selection", MessageKey.HELP_WORLD),
            new HelpLine("selection", MessageKey.HELP_CENTER), new HelpLine("selection", MessageKey.HELP_RADIUS),
            new HelpLine("selection", MessageKey.HELP_CORNERS), new HelpLine("selection", MessageKey.HELP_WORLDBORDER),
            new HelpLine("selection", MessageKey.HELP_SHAPE), new HelpLine("selection", MessageKey.HELP_PATTERN),
            new HelpLine("trim", MessageKey.HELP_TRIM), new HelpLine("silent", MessageKey.HELP_SILENT),
            new HelpLine("silent", MessageKey.HELP_INTERVAL), new HelpLine("doctor", MessageKey.HELP_DOCTOR),
            new HelpLine("reload", MessageKey.HELP_RELOAD));

    private final CommandSupport support;
    private final SelectionCommands selection;
    private final StartCommand start;
    private final TaskCommands tasks;
    private final TrimCommand trim;
    private final AdminCommands admin;

    public PregenCommand(CommandSupport support, BossBarDisplay bossBars, TrimService trims, Doctor doctor,
            PluginControl control, Paths paths, Executor io) {
        this.support = support;
        this.selection = new SelectionCommands(support, paths.csvDirectory());
        this.start = new StartCommand(support, selection, paths.serverProperties());
        this.tasks = new TaskCommands(support, bossBars);
        this.trim = new TrimCommand(support, trims);
        this.admin = new AdminCommands(support, doctor, control, io);
    }

    /** File locations the commands read. */
    public record Paths(Path csvDirectory, Path serverProperties) {
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(NAME)
                .requires(source -> source.getSender().hasPermission(CommandSupport.PERMISSION_PREFIX + "help"))
                .executes(context -> help(sender(context)))
                .then(Commands.literal("help").executes(context -> help(sender(context)))).then(start.build())
                .then(trim.build());
        selection.build().forEach(root::then);
        tasks.build().forEach(root::then);
        admin.build().forEach(root::then);
        return root.build();
    }

    private int help(CommandSender sender) {
        support.send(sender, MessageKey.HELP_HEADER);
        for (HelpLine line : HELP) {
            if (sender.hasPermission(CommandSupport.PERMISSION_PREFIX + line.permission())) {
                support.send(sender, line.key());
            }
        }
        return success();
    }
}
