package dev.pregen.command;

import static dev.pregen.command.CommandSupport.permission;
import static dev.pregen.command.CommandSupport.sender;
import static dev.pregen.command.CommandSupport.success;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.pregen.config.PluginSettings;
import dev.pregen.diagnostics.Doctor;
import dev.pregen.diagnostics.Doctor.Finding;
import dev.pregen.diagnostics.Doctor.WorldInfo;
import dev.pregen.i18n.Arg;
import dev.pregen.i18n.MessageKey;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.command.CommandSender;

/** Operator tools: console verbosity, reload and diagnostics. */
final class AdminCommands {

    private final CommandSupport support;
    private final Doctor doctor;
    private final PluginControl control;
    private final Executor io;

    AdminCommands(CommandSupport support, Doctor doctor, PluginControl control, Executor io) {
        this.support = support;
        this.doctor = doctor;
        this.control = control;
        this.io = io;
    }

    List<LiteralArgumentBuilder<CommandSourceStack>> build() {
        return List.of(silent(), interval(), reload(), doctor());
    }

    private LiteralArgumentBuilder<CommandSourceStack> silent() {
        return Commands.literal("silent").requires(permission("silent")).executes(context -> {
            PluginSettings current = support.settings().get();
            boolean silent = !current.progress().silent();
            updateProgress(current, new PluginSettings.Progress(current.progress().consoleIntervalSeconds(), silent,
                    current.progress().bossBarColor(), current.progress().bossBarOverlay()));
            control.persistSetting("progress.silent", silent);
            support.send(sender(context), silent ? MessageKey.SILENT_ON : MessageKey.SILENT_OFF);
            return success();
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> interval() {
        return Commands.literal("interval").requires(permission("silent"))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600)).executes(context -> {
                    int seconds = IntegerArgumentType.getInteger(context, "seconds");
                    PluginSettings current = support.settings().get();
                    updateProgress(current, new PluginSettings.Progress(seconds, current.progress().silent(),
                            current.progress().bossBarColor(), current.progress().bossBarOverlay()));
                    control.persistSetting("progress.console-interval-seconds", seconds);
                    support.send(sender(context), MessageKey.INTERVAL_SET, Arg.number("seconds", seconds));
                    return success();
                }));
    }

    private void updateProgress(PluginSettings current, PluginSettings.Progress progress) {
        support.settings().set(current.withProgress(progress));
    }

    private LiteralArgumentBuilder<CommandSourceStack> reload() {
        return Commands.literal("reload").requires(permission("reload")).executes(context -> {
            CommandSender sender = sender(context);
            if (support.generation().hasRunningTasks()) {
                support.send(sender, MessageKey.RELOAD_BLOCKED);
                return 0;
            }
            support.whenDone(control.reload(), sender, "reload",
                    ignored -> support.send(sender, MessageKey.RELOAD_DONE));
            return success();
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> doctor() {
        return Commands.literal("doctor").requires(permission("doctor")).executes(context -> {
            CommandSender sender = sender(context);
            CompletableFuture<List<WorldInfo>> worlds = new CompletableFuture<>();
            // World borders belong to the global region on Folia; capture them there, then do the I/O off-thread.
            support.server().getGlobalRegionScheduler().execute(support.plugin(), () -> {
                try {
                    worlds.complete(support.server().getWorlds().stream().map(WorldInfo::of).toList());
                } catch (RuntimeException e) {
                    worlds.completeExceptionally(e);
                }
            });
            support.whenDone(worlds.thenApplyAsync(doctor::examine, io), sender, "doctor",
                    findings -> report(sender, findings));
            return success();
        });
    }

    private void report(CommandSender sender, List<Finding> findings) {
        support.send(sender, MessageKey.DOCTOR_HEADER);
        boolean anyWarning = false;
        for (Finding finding : findings) {
            support.send(sender, finding.key(), finding.args());
            anyWarning |= finding.warning();
        }
        if (!anyWarning) {
            support.send(sender, MessageKey.DOCTOR_OK);
        }
    }
}
