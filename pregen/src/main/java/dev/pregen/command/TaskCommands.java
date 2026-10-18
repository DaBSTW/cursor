package dev.pregen.command;

import static dev.pregen.command.CommandSupport.permission;
import static dev.pregen.command.CommandSupport.sender;
import static dev.pregen.command.CommandSupport.success;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.pregen.api.TaskProgress;
import dev.pregen.api.TaskState;
import dev.pregen.generation.GenerationTask;
import dev.pregen.i18n.Arg;
import dev.pregen.i18n.MessageKey;
import dev.pregen.progress.BossBarDisplay;
import dev.pregen.progress.ProgressReporter;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/** Task control: pause, resume, cancel, confirm, status and the boss bar toggle. */
final class TaskCommands {

    private final CommandSupport support;
    private final BossBarDisplay bossBars;

    TaskCommands(CommandSupport support, BossBarDisplay bossBars) {
        this.support = support;
        this.bossBars = bossBars;
    }

    List<LiteralArgumentBuilder<CommandSourceStack>> build() {
        return List.of(pause(), resume(), cancel(), confirm(), status(), watch());
    }

    private LiteralArgumentBuilder<CommandSourceStack> pause() {
        return Commands.literal("pause").requires(permission("pause"))
                .executes(context -> pause(sender(context), support.generation().pauseAll()))
                .then(worldArgument().executes(context -> {
                    String world = StringArgumentType.getString(context, "world");
                    return pause(sender(context), support.generation().pause(world) ? List.of(world) : List.of());
                }));
    }

    private int pause(CommandSender sender, List<String> paused) {
        if (paused.isEmpty()) {
            support.send(sender, MessageKey.PAUSE_NONE);
            return 0;
        }
        paused.forEach(world -> support.send(sender, MessageKey.PAUSE_DONE, Arg.text("world", world)));
        return success();
    }

    private LiteralArgumentBuilder<CommandSourceStack> resume() {
        return Commands.literal("resume").requires(permission("resume"))
                .executes(context -> reportResumed(sender(context), support.generation().resumeAll()))
                .then(worldArgument().executes(context -> {
                    String world = StringArgumentType.getString(context, "world");
                    return reportResumed(sender(context), support.generation().resume(world)
                            .thenApply(ok -> ok ? List.of(world) : List.<String>of()));
                }));
    }

    private int reportResumed(CommandSender sender, CompletableFuture<List<String>> resumed) {
        support.whenDone(resumed, sender, "resume", worlds -> {
            if (worlds.isEmpty()) {
                support.send(sender, MessageKey.RESUME_NONE);
            }
            worlds.forEach(world -> support.send(sender, MessageKey.RESUME_DONE, Arg.text("world", world)));
        });
        return success();
    }

    private LiteralArgumentBuilder<CommandSourceStack> cancel() {
        return Commands.literal("cancel").requires(permission("cancel"))
                .executes(context -> requestCancel(sender(context), null)).then(worldArgument().executes(
                        context -> requestCancel(sender(context), StringArgumentType.getString(context, "world"))));
    }

    private int requestCancel(CommandSender sender, @Nullable String world) {
        support.send(sender, MessageKey.CANCEL_CONFIRM, Arg.text("target", world == null ? "*" : world));
        support.requestConfirmation(sender, () -> {
            CompletableFuture<List<String>> cancelled = world == null
                    ? support.generation().cancelAll()
                    : support.generation().cancel(world).thenApply(ok -> ok ? List.of(world) : List.<String>of());
            support.whenDone(cancelled, sender, "cancel", worlds -> {
                if (worlds.isEmpty()) {
                    support.send(sender, MessageKey.CANCEL_NONE);
                }
                worlds.forEach(name -> support.send(sender, MessageKey.CANCEL_DONE, Arg.text("world", name)));
            });
        });
        return success();
    }

    private LiteralArgumentBuilder<CommandSourceStack> confirm() {
        return Commands.literal("confirm").requires(permission("confirm")).executes(context -> {
            CommandSender sender = sender(context);
            switch (support.confirm(sender)) {
                case CONFIRMED -> {
                    return success();
                }
                case EXPIRED -> support.send(sender, MessageKey.CONFIRM_EXPIRED);
                case NOTHING_PENDING -> support.send(sender, MessageKey.CONFIRM_NOTHING);
            }
            return 0;
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> status() {
        return Commands.literal("status").requires(permission("status")).executes(context -> {
            CommandSender sender = sender(context);
            List<GenerationTask> tasks = new ArrayList<>(support.generation().tasks());
            if (tasks.isEmpty()) {
                support.send(sender, MessageKey.STATUS_NONE);
                return success();
            }
            support.send(sender, MessageKey.STATUS_HEADER);
            for (GenerationTask task : tasks) {
                TaskProgress progress = task.progress();
                List<Arg> args = new ArrayList<>(List.of(ProgressReporter.args(progress)));
                args.add(Arg.message("state", stateKey(task)));
                support.send(sender, MessageKey.STATUS_LINE, args.toArray(Arg[]::new));
            }
            return success();
        });
    }

    private static MessageKey stateKey(GenerationTask task) {
        if (task.isThrottled()) {
            return MessageKey.STATE_THROTTLED;
        }
        TaskState state = task.state();
        return switch (state) {
            case PREPARING -> MessageKey.STATE_PREPARING;
            case RUNNING -> MessageKey.STATE_RUNNING;
            case PAUSED -> MessageKey.STATE_PAUSED;
            case COMPLETED -> MessageKey.STATE_COMPLETED;
            case CANCELLED -> MessageKey.STATE_CANCELLED;
        };
    }

    private LiteralArgumentBuilder<CommandSourceStack> watch() {
        return Commands.literal("watch").requires(permission("watch")).executes(context -> {
            if (!(sender(context) instanceof Player player)) {
                support.send(sender(context), MessageKey.PLAYER_ONLY);
                return 0;
            }
            support.send(player, bossBars.toggle(player) ? MessageKey.WATCH_ON : MessageKey.WATCH_OFF);
            return success();
        });
    }

    // Saved tasks may belong to worlds that are not loaded, so this is free text with suggestions.
    private RequiredArgumentBuilder<CommandSourceStack, String> worldArgument() {
        return Commands.argument("world", StringArgumentType.string()).suggests(this::suggestWorlds);
    }

    private CompletableFuture<Suggestions> suggestWorlds(CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        TreeSet<String> names = new TreeSet<>();
        support.generation().tasks().forEach(task -> names.add(task.worldName()));
        support.server().getWorlds().stream().map(World::getName).forEach(names::add);
        return CommandSupport.suggest(builder, names);
    }
}
