package dev.pregen.progress;

import dev.pregen.api.TaskProgress;
import dev.pregen.api.TaskState;
import dev.pregen.api.event.PregenTaskProgressEvent;
import dev.pregen.config.SettingsHolder;
import dev.pregen.generation.GenerationService;
import dev.pregen.generation.GenerationTask;
import dev.pregen.i18n.Arg;
import dev.pregen.i18n.MessageKey;
import java.util.List;
import org.bukkit.plugin.PluginManager;

/** Periodic progress output: console lines, boss bars and {@link PregenTaskProgressEvent}s. */
public final class ProgressReporter {

    private final GenerationService generation;
    private final SettingsHolder settings;
    private final Notifier notifier;
    private final BossBarDisplay bossBars;
    private final PluginManager events;
    // Only touched by the heartbeat thread.
    private int secondsSinceConsole;

    public ProgressReporter(GenerationService generation, SettingsHolder settings, Notifier notifier,
            BossBarDisplay bossBars, PluginManager events) {
        this.generation = generation;
        this.settings = settings;
        this.notifier = notifier;
        this.bossBars = bossBars;
        this.events = events;
    }

    /** Called once per second, after the generation heartbeat. */
    public void heartbeat() {
        List<TaskProgress> snapshots = generation.tasks().stream().map(GenerationTask::progress).toList();
        bossBars.update(snapshots);
        if (++secondsSinceConsole < settings.get().progress().consoleIntervalSeconds()) {
            return;
        }
        secondsSinceConsole = 0;
        for (TaskProgress snapshot : snapshots) {
            if (snapshot.state() != TaskState.RUNNING) {
                continue;
            }
            events.callEvent(new PregenTaskProgressEvent(snapshot));
            if (!settings.get().progress().silent()) {
                notifier.console(MessageKey.PROGRESS_CONSOLE, args(snapshot));
            }
        }
    }

    /** Placeholders shared by the console line and {@code /pregen status}. */
    public static Arg[] args(TaskProgress snapshot) {
        return new Arg[]{Arg.text("world", snapshot.worldName()), Arg.decimal("percent", snapshot.percent()),
                Arg.number("processed", snapshot.processedChunks()),
                new Arg.Formatted("total", snapshot.exactTotal() ? "%,d" : "~%,d", snapshot.totalChunks()),
                Arg.decimal("rate", snapshot.chunksPerSecond()), Arg.duration("eta", snapshot.etaSeconds()),
                Arg.duration("elapsed", snapshot.elapsedSeconds()), Arg.text("x", snapshot.lastChunkX()),
                Arg.text("z", snapshot.lastChunkZ())};
    }
}
