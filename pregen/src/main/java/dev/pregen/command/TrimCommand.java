package dev.pregen.command;

import static dev.pregen.command.CommandSupport.permission;
import static dev.pregen.command.CommandSupport.sender;
import static dev.pregen.command.CommandSupport.success;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.pregen.diagnostics.ByteSize;
import dev.pregen.i18n.Arg;
import dev.pregen.i18n.MessageKey;
import dev.pregen.trim.PendingTrim;
import dev.pregen.trim.TrimPlanner;
import dev.pregen.trim.TrimRequest;
import dev.pregen.trim.TrimService;
import dev.pregen.trim.TrimService.Planned;
import dev.pregen.trim.TrimService.PreviewProblem;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import org.bukkit.command.CommandSender;

/**
 * {@code /pregen trim <outside|inside> [inhabited <ticks>]}, {@code trim pending} and {@code trim unschedule}. Nothing
 * is deleted here: the command previews, then {@code /pregen confirm} schedules the trim for the next startup.
 */
final class TrimCommand {

    private final CommandSupport support;
    private final TrimService trims;

    TrimCommand(CommandSupport support, TrimService trims) {
        this.support = support;
        this.trims = trims;
    }

    LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("trim").requires(permission("trim")).then(mode("outside", false))
                .then(mode("inside", true))
                .then(Commands.literal("pending").executes(context -> listPending(sender(context))))
                .then(Commands.literal("unschedule")
                        .then(Commands.argument("world", StringArgumentType.string())
                                .suggests((context, builder) -> trims.pending()
                                        .thenCompose(list -> CommandSupport.suggest(builder,
                                                list.stream().map(PendingTrim::worldName).toList())))
                                .executes(context -> unschedule(sender(context),
                                        StringArgumentType.getString(context, "world")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> mode(String literal, boolean deleteInside) {
        return Commands.literal(literal).executes(context -> preview(sender(context), deleteInside, -1))
                .then(Commands.literal("inhabited")
                        .then(Commands.argument("ticks", LongArgumentType.longArg(0))
                                .executes(context -> preview(sender(context), deleteInside,
                                        LongArgumentType.getLong(context, "ticks")))));
    }

    private int preview(CommandSender sender, boolean deleteInside, long maxInhabitedTicks) {
        TrimRequest request = new TrimRequest(support.selection(sender), deleteInside, maxInhabitedTicks);
        Arg world = Arg.text("world", request.selection().worldName());
        support.send(sender, MessageKey.TRIM_PLANNING, world);
        support.whenDone(trims.preview(request), sender, "trim-preview", outcome -> {
            switch (outcome) {
                case PreviewProblem problem -> support.send(sender, switch (problem) {
                    case WORLD_NOT_FOUND -> MessageKey.WORLD_NOT_FOUND;
                    case TASK_RUNNING -> MessageKey.TRIM_TASK_RUNNING;
                    case NO_STORAGE -> MessageKey.TRIM_NO_STORAGE;
                }, world);
                case Planned planned -> showPlan(sender, planned);
            }
        });
        return success();
    }

    private void showPlan(CommandSender sender, Planned planned) {
        TrimPlanner.Preview preview = planned.preview();
        if (preview.isEmpty()) {
            support.send(sender, MessageKey.TRIM_NOTHING);
            return;
        }
        TrimRequest request = planned.request();
        support.send(sender, MessageKey.TRIM_PREVIEW, Arg.text("world", request.selection().worldName()),
                Arg.message("mode", modeKey(request)), Arg.number("chunks", preview.chunks()),
                Arg.number("regions", preview.regionsAffected()),
                Arg.number("deleted_files", preview.regionsDeletedEntirely()),
                Arg.text("size", ByteSize.format(preview.bytes())));
        if (request.hasInhabitedFilter()) {
            support.send(sender, MessageKey.TRIM_PREVIEW_INHABITED, Arg.number("ticks", request.maxInhabitedTicks()));
        }
        if (support.settings().get().trim().backup()) {
            support.send(sender, MessageKey.TRIM_BACKUP_ON,
                    Arg.text("directory", support.settings().get().trim().backupDirectory()));
        } else {
            support.send(sender, MessageKey.TRIM_BACKUP_OFF);
        }
        support.send(sender, MessageKey.TRIM_CONFIRM_HINT);
        support.requestConfirmation(sender, () -> support.whenDone(trims.schedule(planned, sender.getName()), sender,
                "trim-schedule", outcome -> support.send(sender, switch (outcome) {
                    case SCHEDULED -> MessageKey.TRIM_SCHEDULED;
                    case CANCELLED_BY_LISTENER -> MessageKey.TRIM_CANCELLED_BY_LISTENER;
                    case TASK_RUNNING -> MessageKey.TRIM_TASK_RUNNING;
                }, Arg.text("world", request.selection().worldName()))));
    }

    private int listPending(CommandSender sender) {
        support.whenDone(trims.pending(), sender, "trim-pending", list -> showPending(sender, list));
        return success();
    }

    private void showPending(CommandSender sender, List<PendingTrim> list) {
        if (list.isEmpty()) {
            support.send(sender, MessageKey.TRIM_PENDING_NONE);
            return;
        }
        support.send(sender, MessageKey.TRIM_PENDING_HEADER);
        for (PendingTrim trim : list) {
            support.send(sender, MessageKey.TRIM_PENDING_ENTRY, Arg.text("world", trim.worldName()),
                    Arg.message("mode",
                            trim.deleteInside() ? MessageKey.TRIM_MODE_INSIDE : MessageKey.TRIM_MODE_OUTSIDE),
                    Arg.text("shape", trim.selection().shape()),
                    Arg.text("x", CommandSupport.formatNumber(trim.selection().centerX())),
                    Arg.text("z", CommandSupport.formatNumber(trim.selection().centerZ())),
                    Arg.text("radius", CommandSupport.formatNumber(trim.selection().radiusX())),
                    Arg.text("by", trim.requestedBy()));
        }
    }

    private int unschedule(CommandSender sender, String world) {
        support.whenDone(trims.unschedule(world), sender, "trim-unschedule", removed -> support.send(sender,
                removed ? MessageKey.TRIM_UNSCHEDULED : MessageKey.TRIM_UNSCHEDULE_UNKNOWN, Arg.text("world", world)));
        return success();
    }

    private static MessageKey modeKey(TrimRequest request) {
        return request.deleteInside() ? MessageKey.TRIM_MODE_INSIDE : MessageKey.TRIM_MODE_OUTSIDE;
    }
}
