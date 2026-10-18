package dev.pregen.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.pregen.config.SettingsHolder;
import dev.pregen.generation.GenerationService;
import dev.pregen.i18n.Arg;
import dev.pregen.i18n.MessageKey;
import dev.pregen.i18n.MessageService;
import dev.pregen.selection.Selection;
import dev.pregen.selection.SelectionRegistry;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Shared dependencies and helpers for the {@code /pregen} subcommands. */
public final class CommandSupport {

    /** Key for the console and other non-player senders, which share one selection and confirmation slot. */
    static final UUID CONSOLE_ID = new UUID(0L, 0L);
    static final String PERMISSION_PREFIX = "pregen.command.";

    private final Plugin plugin;
    private final MessageService messages;
    private final SettingsHolder settings;
    private final GenerationService generation;
    private final SelectionRegistry selections;
    private final ConfirmationService confirmations;

    public CommandSupport(Plugin plugin, MessageService messages, SettingsHolder settings, GenerationService generation,
            SelectionRegistry selections, ConfirmationService confirmations) {
        this.plugin = plugin;
        this.messages = messages;
        this.settings = settings;
        this.generation = generation;
        this.selections = selections;
        this.confirmations = confirmations;
    }

    public Plugin plugin() {
        return plugin;
    }

    public Server server() {
        return plugin.getServer();
    }

    public Logger logger() {
        return plugin.getLogger();
    }

    public MessageService messages() {
        return messages;
    }

    public SettingsHolder settings() {
        return settings;
    }

    public GenerationService generation() {
        return generation;
    }

    public static Predicate<CommandSourceStack> permission(String node) {
        return source -> source.getSender().hasPermission(PERMISSION_PREFIX + node);
    }

    public static UUID idOf(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE_ID;
    }

    public static CommandSender sender(CommandContext<CommandSourceStack> context) {
        return context.getSource().getSender();
    }

    public void send(CommandSender sender, MessageKey key, Arg... args) {
        messages.send(sender, key, args);
    }

    public Selection selection(CommandSender sender) {
        return selections.get(idOf(sender), id -> defaultSelection(sender));
    }

    public Selection updateSelection(CommandSender sender, UnaryOperator<Selection> change) {
        return selections.update(idOf(sender), id -> defaultSelection(sender), change);
    }

    public void requestConfirmation(CommandSender sender, Runnable action) {
        long timeout = settings.get().confirmTimeoutSeconds() * 1000L;
        confirmations.request(idOf(sender), action, System.currentTimeMillis(), timeout);
    }

    public ConfirmationService.Result confirm(CommandSender sender) {
        return confirmations.confirm(idOf(sender), System.currentTimeMillis());
    }

    /** Ends an async command chain: unexpected failures are logged and reported instead of vanishing. */
    public <T> void whenDone(CompletableFuture<T> future, CommandSender sender, String action, Consumer<T> onSuccess) {
        future.whenComplete((value, error) -> {
            if (error != null) {
                logger().log(Level.SEVERE, "Command failed: action=" + action + " sender=" + sender.getName(), error);
                send(sender, MessageKey.INTERNAL_ERROR);
                return;
            }
            onSuccess.accept(value);
        });
    }

    /**
     * World argument by Bukkit name ({@code world_nether}), suggesting loaded worlds. Paper's own world argument takes
     * dimension keys ({@code minecraft:the_nether}), which do not match the names admins know.
     */
    public RequiredArgumentBuilder<CommandSourceStack, String> worldArgument() {
        return Commands.argument("world", StringArgumentType.string()).suggests(
                (context, builder) -> suggest(builder, server().getWorlds().stream().map(World::getName).toList()));
    }

    /** Resolves the world argument by name, or by namespaced key; reports and returns empty when unknown. */
    public Optional<World> resolveWorld(CommandContext<CommandSourceStack> context) {
        String raw = StringArgumentType.getString(context, "world");
        World world = server().getWorld(raw);
        if (world == null) {
            NamespacedKey key = NamespacedKey.fromString(raw);
            world = key == null ? null : server().getWorld(key);
        }
        if (world == null) {
            send(sender(context), MessageKey.WORLD_NOT_FOUND, Arg.text("world", raw));
        }
        return Optional.ofNullable(world);
    }

    public static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Collection<String> options) {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(typed)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }

    public static String formatRadius(Selection selection) {
        double radiusZ = selection.effectiveRadiusZ();
        return radiusZ == selection.radiusX()
                ? formatNumber(selection.radiusX())
                : formatNumber(selection.radiusX()) + " x " + formatNumber(radiusZ);
    }

    public static String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    public static int success() {
        return Command.SINGLE_SUCCESS;
    }

    private Selection defaultSelection(CommandSender sender) {
        if (sender instanceof Player player) {
            return Selection.defaults(player.getWorld().getName());
        }
        return Selection.defaults(server().getWorlds().stream().findFirst().map(World::getName).orElse("world"));
    }
}
