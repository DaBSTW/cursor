package dev.pregen.command;

import static dev.pregen.command.CommandSupport.permission;
import static dev.pregen.command.CommandSupport.sender;
import static dev.pregen.command.CommandSupport.success;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.pregen.geometry.ShapeType;
import dev.pregen.i18n.Arg;
import dev.pregen.i18n.MessageKey;
import dev.pregen.pattern.PatternType;
import dev.pregen.selection.Selection;
import dev.pregen.util.RadiusInput;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/** Commands that edit or show the sender's selection. */
final class SelectionCommands {

    private static final double MAX_COORDINATE = RadiusInput.MAX_RADIUS;
    // Vanilla's default border is 59,999,968 blocks wide; anything that large means "no border configured".
    private static final double DEFAULT_BORDER_SIZE = 5.9e7;
    private static final String NODE = "selection";

    private final CommandSupport support;
    private final Path csvDirectory;

    SelectionCommands(CommandSupport support, Path csvDirectory) {
        this.support = support;
        this.csvDirectory = csvDirectory;
    }

    List<LiteralArgumentBuilder<CommandSourceStack>> build() {
        return List.of(selection(), world(), center(), radius(), corners(), worldBorder(), shape(), pattern());
    }

    private LiteralArgumentBuilder<CommandSourceStack> selection() {
        return Commands.literal("selection").requires(permission(NODE)).executes(context -> {
            CommandSender sender = sender(context);
            support.send(sender, MessageKey.SELECTION_HEADER);
            support.send(sender, MessageKey.SELECTION_DETAILS, details(support.selection(sender)));
            return success();
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> world() {
        return Commands.literal("world").requires(permission(NODE)).then(support.worldArgument().executes(context -> {
            Optional<World> resolved = support.resolveWorld(context);
            if (resolved.isEmpty()) {
                return 0;
            }
            World world = resolved.get();
            support.updateSelection(sender(context), selection -> selection.withWorld(world.getName()));
            support.send(sender(context), MessageKey.SELECTION_WORLD, Arg.text("world", world.getName()));
            return success();
        }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> center() {
        return Commands.literal("center").requires(permission(NODE))
                .then(Commands.literal("here").executes(this::centerHere))
                .then(Commands.literal("spawn").executes(this::centerSpawn))
                .then(Commands.argument("x", DoubleArgumentType.doubleArg(-MAX_COORDINATE, MAX_COORDINATE))
                        .then(Commands.argument("z", DoubleArgumentType.doubleArg(-MAX_COORDINATE, MAX_COORDINATE))
                                .executes(context -> setCenter(sender(context),
                                        DoubleArgumentType.getDouble(context, "x"),
                                        DoubleArgumentType.getDouble(context, "z")))));
    }

    private int centerHere(CommandContext<CommandSourceStack> context) {
        if (!(sender(context) instanceof Player player)) {
            support.send(sender(context), MessageKey.PLAYER_ONLY);
            return 0;
        }
        // Commands run on the player's own thread (also on Folia), so reading its location here is safe.
        Location location = player.getLocation();
        support.updateSelection(player, selection -> selection.withWorld(player.getWorld().getName()));
        return setCenter(player, location.getBlockX(), location.getBlockZ());
    }

    private int centerSpawn(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        String worldName = support.selection(sender).worldName();
        // World data belongs to the global region on Folia; reading it there is valid on Paper too.
        support.server().getGlobalRegionScheduler().execute(support.plugin(), () -> {
            World world = support.server().getWorld(worldName);
            if (world == null) {
                support.send(sender, MessageKey.WORLD_NOT_FOUND, Arg.text("world", worldName));
                return;
            }
            Location spawn = world.getSpawnLocation();
            setCenter(sender, spawn.getBlockX(), spawn.getBlockZ());
        });
        return success();
    }

    private int setCenter(CommandSender sender, double x, double z) {
        support.updateSelection(sender, selection -> selection.withCenter(x, z));
        support.send(sender, MessageKey.SELECTION_CENTER, Arg.text("x", CommandSupport.formatNumber(x)),
                Arg.text("z", CommandSupport.formatNumber(z)));
        return success();
    }

    private LiteralArgumentBuilder<CommandSourceStack> radius() {
        return Commands.literal("radius").requires(permission(NODE)).then(Commands
                .argument("radius", StringArgumentType.word())
                .executes(context -> setRadius(sender(context), StringArgumentType.getString(context, "radius"), null))
                .then(Commands.argument("radiusZ", StringArgumentType.word())
                        .executes(context -> setRadius(sender(context), StringArgumentType.getString(context, "radius"),
                                StringArgumentType.getString(context, "radiusZ")))));
    }

    /** Applies a radius (and z radius when given); returns 0 and reports when the input is invalid. */
    int setRadius(CommandSender sender, String rawX, @Nullable String rawZ) {
        Selection current = support.selection(sender);
        Optional<Double> radiusX = RadiusInput.parse(rawX).flatMap(input -> input.applyTo(current.radiusX()));
        if (radiusX.isEmpty()) {
            support.send(sender, MessageKey.INVALID_RADIUS, Arg.text("input", rawX));
            return 0;
        }
        if (rawZ == null) {
            support.updateSelection(sender, selection -> selection.withRadius(radiusX.get()));
            support.send(sender, MessageKey.SELECTION_RADIUS,
                    Arg.text("radius", CommandSupport.formatNumber(radiusX.get())));
            return success();
        }
        Optional<Double> radiusZ = RadiusInput.parse(rawZ).flatMap(input -> input.applyTo(current.radiusZ()));
        if (radiusZ.isEmpty()) {
            support.send(sender, MessageKey.INVALID_RADIUS, Arg.text("input", rawZ));
            return 0;
        }
        setRadii(sender, radiusX.get(), radiusZ.get());
        return success();
    }

    private void setRadii(CommandSender sender, double radiusX, double radiusZ) {
        ShapeType before = support.selection(sender).shape();
        Selection updated = support.updateSelection(sender, selection -> {
            Selection resized = selection.withRadii(radiusX, radiusZ);
            // Two different radii only make sense for shapes that honour them.
            if (radiusX != radiusZ && !resized.shape().usesSecondRadius()) {
                return resized.withShape(resized.shape() == ShapeType.CIRCLE ? ShapeType.ELLIPSE : ShapeType.RECTANGLE);
            }
            return resized;
        });
        support.send(sender, MessageKey.SELECTION_RADII, Arg.text("radius_x", CommandSupport.formatNumber(radiusX)),
                Arg.text("radius_z", CommandSupport.formatNumber(radiusZ)));
        if (updated.shape() != before) {
            support.send(sender, MessageKey.SELECTION_SHAPE, Arg.text("shape", updated.shape().id()));
        }
    }

    private LiteralArgumentBuilder<CommandSourceStack> corners() {
        DoubleArgumentType coordinate = DoubleArgumentType.doubleArg(-MAX_COORDINATE, MAX_COORDINATE);
        return Commands.literal("corners").requires(permission(NODE))
                .then(Commands.argument("x1", coordinate)
                        .then(Commands.argument("z1", coordinate).then(Commands.argument("x2", coordinate)
                                .then(Commands.argument("z2", coordinate).executes(this::setCorners)))));
    }

    private int setCorners(CommandContext<CommandSourceStack> context) {
        double x1 = DoubleArgumentType.getDouble(context, "x1");
        double z1 = DoubleArgumentType.getDouble(context, "z1");
        double x2 = DoubleArgumentType.getDouble(context, "x2");
        double z2 = DoubleArgumentType.getDouble(context, "z2");
        double radiusX = Math.abs(x2 - x1) / 2;
        double radiusZ = Math.abs(z2 - z1) / 2;
        CommandSender sender = sender(context);
        if (radiusX < 1 || radiusZ < 1) {
            support.send(sender, MessageKey.INVALID_RADIUS, Arg.text("input", x1 + " " + z1 + " " + x2 + " " + z2));
            return 0;
        }
        setCenter(sender, (x1 + x2) / 2, (z1 + z2) / 2);
        support.updateSelection(sender, selection -> selection.withShape(ShapeType.RECTANGLE));
        setRadii(sender, radiusX, radiusZ);
        return success();
    }

    private LiteralArgumentBuilder<CommandSourceStack> worldBorder() {
        return Commands.literal("worldborder").requires(permission(NODE)).executes(context -> {
            CommandSender sender = sender(context);
            String worldName = support.selection(sender).worldName();
            support.server().getGlobalRegionScheduler().execute(support.plugin(), () -> applyBorder(sender, worldName));
            return success();
        });
    }

    private void applyBorder(CommandSender sender, String worldName) {
        World world = support.server().getWorld(worldName);
        if (world == null) {
            support.send(sender, MessageKey.WORLD_NOT_FOUND, Arg.text("world", worldName));
            return;
        }
        WorldBorder border = world.getWorldBorder();
        if (border.getSize() >= DEFAULT_BORDER_SIZE) {
            support.send(sender, MessageKey.SELECTION_NO_BORDER, Arg.text("world", worldName));
            return;
        }
        double radius = border.getSize() / 2;
        support.updateSelection(sender, selection -> selection.withShape(ShapeType.SQUARE)
                .withCenter(border.getCenter().getX(), border.getCenter().getZ()).withRadius(radius));
        support.send(sender, MessageKey.SELECTION_DETAILS, details(support.selection(sender)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> shape() {
        return Commands.literal("shape").requires(permission(NODE))
                .then(Commands.argument("shape", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSupport.suggest(builder, ShapeType.ids()))
                        .executes(context -> {
                            String raw = StringArgumentType.getString(context, "shape");
                            Optional<ShapeType> shape = ShapeType.fromId(raw);
                            if (shape.isEmpty()) {
                                support.send(sender(context), MessageKey.INVALID_SHAPE, Arg.text("input", raw),
                                        Arg.text("options", String.join(", ", ShapeType.ids())));
                                return 0;
                            }
                            support.updateSelection(sender(context), selection -> selection.withShape(shape.get()));
                            support.send(sender(context), MessageKey.SELECTION_SHAPE,
                                    Arg.text("shape", shape.get().id()));
                            return success();
                        }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> pattern() {
        return Commands.literal("pattern").requires(permission(NODE)).then(Commands
                .argument("pattern", StringArgumentType.word())
                .suggests((context, builder) -> CommandSupport.suggest(builder, PatternType.ids()))
                .executes(context -> setPattern(sender(context), StringArgumentType.getString(context, "pattern"), ""))
                .then(Commands.argument("file", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSupport.suggest(builder, csvFiles())).executes(
                                context -> setPattern(sender(context), StringArgumentType.getString(context, "pattern"),
                                        StringArgumentType.getString(context, "file")))));
    }

    private int setPattern(CommandSender sender, String raw, String argument) {
        Optional<PatternType> pattern = PatternType.fromId(raw);
        if (pattern.isEmpty()) {
            support.send(sender, MessageKey.INVALID_PATTERN, Arg.text("input", raw),
                    Arg.text("options", String.join(", ", PatternType.ids())));
            return 0;
        }
        String file = pattern.get().requiresArgument() ? argument : "";
        if (pattern.get().requiresArgument() && !Selection.isValidFileName(file)) {
            support.send(sender, MessageKey.INVALID_FILE_NAME, Arg.text("input", argument));
            return 0;
        }
        support.updateSelection(sender, selection -> selection.withPattern(pattern.get(), file));
        support.send(sender, MessageKey.SELECTION_PATTERN,
                Arg.text("pattern", file.isEmpty() ? pattern.get().id() : pattern.get().id() + " (" + file + ")"));
        return success();
    }

    private List<String> csvFiles() {
        if (!Files.isDirectory(csvDirectory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(csvDirectory)) {
            return files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".csv"))
                    .map(name -> name.substring(0, name.length() - 4)).filter(Selection::isValidFileName).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    static Arg[] details(Selection selection) {
        String pattern = selection.patternArgument().isEmpty()
                ? selection.pattern().id()
                : selection.pattern().id() + " (" + selection.patternArgument() + ")";
        return new Arg[]{Arg.text("world", selection.worldName()),
                Arg.text("x", CommandSupport.formatNumber(selection.centerX())),
                Arg.text("z", CommandSupport.formatNumber(selection.centerZ())),
                Arg.text("radius", CommandSupport.formatRadius(selection)), Arg.text("shape", selection.shape().id()),
                Arg.text("pattern", pattern)};
    }
}
