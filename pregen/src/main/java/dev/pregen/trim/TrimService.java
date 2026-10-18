package dev.pregen.trim;

import dev.pregen.api.TaskState;
import dev.pregen.api.event.PregenTrimScheduledEvent;
import dev.pregen.config.SettingsHolder;
import dev.pregen.generation.GenerationService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;

/**
 * The three-step trim workflow: dry run, confirmation into the pending list, execution at the next startup before
 * worlds load. Files are never modified while their world is loaded.
 */
public final class TrimService {

    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public sealed interface PreviewOutcome {
    }

    public record Planned(TrimRequest request, StorageDirs dirs,
            TrimPlanner.Preview preview) implements PreviewOutcome {
    }

    public enum PreviewProblem implements PreviewOutcome {
        WORLD_NOT_FOUND,
        TASK_RUNNING,
        NO_STORAGE
    }

    public enum ScheduleOutcome {
        SCHEDULED,
        CANCELLED_BY_LISTENER,
        TASK_RUNNING
    }

    private final Server server;
    private final PendingTrimStore store;
    private final SettingsHolder settings;
    private final GenerationService generation;
    private final Path dataFolder;
    private final Executor io;
    private final Logger logger;

    public TrimService(Server server, PendingTrimStore store, SettingsHolder settings, GenerationService generation,
            Path dataFolder, Executor io, Logger logger) {
        this.server = server;
        this.store = store;
        this.settings = settings;
        this.generation = generation;
        this.dataFolder = dataFolder;
        this.io = io;
        this.logger = logger;
    }

    public CompletableFuture<PreviewOutcome> preview(TrimRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            World world = server.getWorld(request.selection().worldName());
            if (world == null) {
                return PreviewProblem.WORLD_NOT_FOUND;
            }
            if (hasActiveTask(world.getName())) {
                return PreviewProblem.TASK_RUNNING;
            }
            StorageDirs dirs = StorageDirs.locate(world);
            if (!dirs.exists()) {
                return PreviewProblem.NO_STORAGE;
            }
            try {
                return new Planned(request, dirs, TrimPlanner.plan(request, dirs));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, io);
    }

    public CompletableFuture<ScheduleOutcome> schedule(Planned planned, String requestedBy) {
        return CompletableFuture.supplyAsync(() -> {
            String world = planned.request().selection().worldName();
            if (hasActiveTask(world)) {
                return ScheduleOutcome.TASK_RUNNING;
            }
            PregenTrimScheduledEvent event = new PregenTrimScheduledEvent(world, planned.request().deleteInside(),
                    planned.preview().chunks());
            server.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return ScheduleOutcome.CANCELLED_BY_LISTENER;
            }
            String id = sanitize(world) + "-" + LocalDateTime.now().format(ID_TIME);
            try {
                store.put(
                        PendingTrim.of(id, planned.request(), planned.dirs(), System.currentTimeMillis(), requestedBy));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            logger.info("Trim scheduled for next startup: id=" + id + " world=" + world + " by=" + requestedBy
                    + " estimatedChunks=" + planned.preview().chunks());
            return ScheduleOutcome.SCHEDULED;
        }, io);
    }

    public CompletableFuture<List<PendingTrim>> pending() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return store.list();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, io);
    }

    public CompletableFuture<Boolean> unschedule(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return store.remove(worldName);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, io);
    }

    /** Blocking; used by the task launcher on its own I/O thread. */
    public boolean hasPending(String worldName) {
        try {
            return store.find(worldName).isPresent();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Cannot read pending trims; assuming none for world=" + worldName, e);
            return false;
        }
    }

    /**
     * Executes every pending trim. Blocking by design: it runs during plugin enable at {@code STARTUP}, and worlds must
     * not load until it has finished.
     */
    public void runPendingAtStartup() {
        List<PendingTrim> trims;
        try {
            trims = store.list();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Cannot read pending trims; none were executed", e);
            return;
        }
        for (PendingTrim trim : trims) {
            runOne(trim);
        }
    }

    private void runOne(PendingTrim trim) {
        if (server.getWorld(trim.worldName()) != null) {
            logger.severe("Skipping trim because the world is already loaded (was the plugin reloaded at runtime?):"
                    + " id=" + trim.id() + " world=" + trim.worldName() + ". It stays scheduled.");
            return;
        }
        Optional<TrimRequest> request = trim.toRequest();
        if (request.isEmpty()) {
            logger.severe("Discarding invalid pending trim: id=" + trim.id() + " world=" + trim.worldName());
            removeQuietly(trim.worldName());
            return;
        }
        Path backup = settings.get().trim().backup()
                ? dataFolder.resolve(settings.get().trim().backupDirectory()).resolve(trim.id())
                : null;
        logger.info("Running scheduled trim: id=" + trim.id() + " world=" + trim.worldName() + " backup="
                + (backup == null ? "disabled" : backup));
        try {
            TrimExecutor.Result result = new TrimExecutor(request.get(), trim.dirs(), backup, System::currentTimeMillis)
                    .run();
            removeQuietly(trim.worldName());
            logger.info("Trim finished: id=" + trim.id() + " regionsModified=" + result.regionsModified()
                    + " chunksDeleted=" + result.chunksDeleted() + " unreadableChunksKept="
                    + result.unreadableChunksKept() + " millis=" + result.millis());
        } catch (IOException | RuntimeException e) {
            logger.log(Level.SEVERE, "Trim failed and stays scheduled; it will be retried on the next startup:" + " id="
                    + trim.id() + " world=" + trim.worldName(), e);
        }
    }

    private boolean hasActiveTask(String worldName) {
        return generation.task(worldName).map(task -> task.state() != TaskState.COMPLETED).orElse(false);
    }

    private void removeQuietly(String worldName) {
        try {
            store.remove(worldName);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to remove pending trim entry: world=" + worldName, e);
        }
    }

    private static String sanitize(String worldName) {
        return worldName.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
