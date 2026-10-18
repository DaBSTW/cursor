package dev.pregen.command;

import static dev.pregen.command.CommandSupport.permission;
import static dev.pregen.command.CommandSupport.sender;
import static dev.pregen.command.CommandSupport.success;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.pregen.diagnostics.ByteSize;
import dev.pregen.diagnostics.ServerProperties;
import dev.pregen.generation.StartResult;
import dev.pregen.geometry.ShapeType;
import dev.pregen.i18n.Arg;
import dev.pregen.i18n.MessageKey;
import dev.pregen.selection.Selection;
import dev.pregen.util.RadiusInput;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

/** {@code /pregen start [world] [shape] [radius] [x z]}: optional arguments update the selection, then start. */
final class StartCommand {

    private final CommandSupport support;
    private final SelectionCommands selectionCommands;
    private final Path serverProperties;

    StartCommand(CommandSupport support, SelectionCommands selectionCommands, Path serverProperties) {
        this.support = support;
        this.selectionCommands = selectionCommands;
        this.serverProperties = serverProperties;
    }

    LiteralArgumentBuilder<CommandSourceStack> build() {
        double max = RadiusInput.MAX_RADIUS;
        return Commands.literal("start").requires(permission("start")).executes(context -> start(sender(context)))
                .then(Commands.argument("world", ArgumentTypes.world())
                        .executes(context -> applyAndStart(context, false, false, false))
                        .then(Commands.argument("shape", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSupport.suggest(builder, ShapeType.ids()))
                                .executes(context -> applyAndStart(context, true, false, false))
                                .then(Commands.argument("radius", StringArgumentType.word())
                                        .executes(context -> applyAndStart(context, true, true, false))
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg(-max, max))
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg(-max, max))
                                                        .executes(context -> applyAndStart(context, true, true,
                                                                true)))))));
    }

    private int applyAndStart(CommandContext<CommandSourceStack> context, boolean hasShape, boolean hasRadius,
            boolean hasCenter) {
        CommandSender sender = sender(context);
        World world = context.getArgument("world", World.class);
        if (hasShape) {
            String raw = StringArgumentType.getString(context, "shape");
            Optional<ShapeType> shape = ShapeType.fromId(raw);
            if (shape.isEmpty()) {
                support.send(sender, MessageKey.INVALID_SHAPE, Arg.text("input", raw),
                        Arg.text("options", String.join(", ", ShapeType.ids())));
                return 0;
            }
            support.updateSelection(sender, selection -> selection.withShape(shape.get()));
        }
        if (hasRadius
                && selectionCommands.setRadius(sender, StringArgumentType.getString(context, "radius"), null) == 0) {
            return 0;
        }
        support.updateSelection(sender, selection -> {
            Selection updated = selection.withWorld(world.getName());
            return hasCenter
                    ? updated.withCenter(DoubleArgumentType.getDouble(context, "x"),
                            DoubleArgumentType.getDouble(context, "z"))
                    : updated;
        });
        return start(sender);
    }

    private int start(CommandSender sender) {
        Selection selection = support.selection(sender);
        support.send(sender, MessageKey.START_PREPARING, Arg.text("world", selection.worldName()));
        launch(sender, selection, false);
        return success();
    }

    private void launch(CommandSender sender, Selection selection, boolean ignoreDiskSpace) {
        CompletableFuture<StartResult> result = support.generation().start(selection, ignoreDiskSpace);
        support.whenDone(result, sender, "start", outcome -> report(sender, selection, outcome));
    }

    private void report(CommandSender sender, Selection selection, StartResult result) {
        Arg world = Arg.text("world", selection.worldName());
        switch (result.outcome()) {
            case STARTED -> {
                support.send(sender, MessageKey.START_STARTED, world, Arg.text("shape", selection.shape().id()),
                        Arg.text("x", CommandSupport.formatNumber(selection.centerX())),
                        Arg.text("z", CommandSupport.formatNumber(selection.centerZ())),
                        Arg.text("radius", CommandSupport.formatRadius(selection)),
                        Arg.number("total", result.totalChunks()));
                warnIfServerPausesWhenEmpty(sender);
            }
            case ALREADY_RUNNING -> support.send(sender, MessageKey.START_ALREADY_RUNNING, world);
            case SAVED_TASK_EXISTS -> support.send(sender, MessageKey.START_SAVED_TASK, world);
            case WORLD_NOT_FOUND -> support.send(sender, MessageKey.WORLD_NOT_FOUND, world);
            case RADIUS_LIMIT_EXCEEDED -> support.send(sender, MessageKey.START_RADIUS_LIMIT, Arg.text("limit",
                    CommandSupport.formatNumber(support.settings().get().generation().radiusLimit())));
            case INSUFFICIENT_DISK_SPACE -> {
                support.send(sender, MessageKey.START_DISK_SPACE, Arg.text("free", ByteSize.format(result.freeBytes())),
                        Arg.text("required", ByteSize.format(result.requiredBytes())));
                support.requestConfirmation(sender, () -> launch(sender, selection, true));
            }
            case MISSING_INPUT_FILE ->
                support.send(sender, MessageKey.START_MISSING_FILE, Arg.text("file", selection.patternArgument()));
            case EMPTY_SELECTION -> support.send(sender, MessageKey.START_EMPTY);
            case TRIM_PENDING -> support.send(sender, MessageKey.START_TRIM_PENDING, world);
            case CANCELLED_BY_LISTENER -> support.send(sender, MessageKey.START_CANCELLED_BY_LISTENER);
            case FAILED -> support.send(sender, MessageKey.INTERNAL_ERROR);
        }
    }

    private void warnIfServerPausesWhenEmpty(CommandSender sender) {
        OptionalInt seconds = ServerProperties.pauseWhenEmptySeconds(serverProperties);
        if (seconds.isPresent()) {
            support.send(sender, MessageKey.START_PAUSE_WHEN_EMPTY, Arg.number("seconds", seconds.getAsInt()));
        }
    }
}
