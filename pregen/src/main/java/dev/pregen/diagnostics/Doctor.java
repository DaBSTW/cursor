package dev.pregen.diagnostics;

import dev.pregen.generation.GenerationService;
import dev.pregen.i18n.Arg;
import dev.pregen.i18n.MessageKey;
import dev.pregen.persistence.TaskStore;
import dev.pregen.trim.PendingTrimStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.World;

/** Checks the server setup for things that silently hurt pre-generation. Blocking; run it off the main thread. */
public final class Doctor {

    /** One line of the report. Warnings decide whether the "no problems" line is shown. */
    public record Finding(MessageKey key, boolean warning, Arg... args) {
    }

    /** World data captured on a server thread, since borders must be read there on Folia. */
    public record WorldInfo(String name, Path folder, double borderRadius) {
        public static WorldInfo of(World world) {
            return new WorldInfo(world.getName(), world.getWorldFolder().toPath(),
                    world.getWorldBorder().getSize() / 2);
        }
    }

    private final Path serverProperties;
    private final GenerationService generation;
    private final TaskStore taskStore;
    private final PendingTrimStore trimStore;
    private final Logger logger;

    public Doctor(Path serverProperties, GenerationService generation, TaskStore taskStore, PendingTrimStore trimStore,
            Logger logger) {
        this.serverProperties = serverProperties;
        this.generation = generation;
        this.taskStore = taskStore;
        this.trimStore = trimStore;
        this.logger = logger;
    }

    public List<Finding> examine(List<WorldInfo> worlds) {
        List<Finding> findings = new ArrayList<>();
        OptionalInt pause = ServerProperties.pauseWhenEmptySeconds(serverProperties);
        if (pause.isPresent()) {
            findings.add(
                    new Finding(MessageKey.DOCTOR_PAUSE_WHEN_EMPTY, true, Arg.number("seconds", pause.getAsInt())));
        }
        if (!generation.throttle().isProbeAvailable()) {
            findings.add(new Finding(MessageKey.DOCTOR_THROTTLE_UNAVAILABLE, true));
        }
        try {
            int pendingTrims = trimStore.list().size();
            if (pendingTrims > 0) {
                findings.add(new Finding(MessageKey.DOCTOR_PENDING_TRIMS, true, Arg.number("count", pendingTrims)));
            }
            findings.add(
                    new Finding(MessageKey.DOCTOR_SAVED_TASKS, false, Arg.number("count", taskStore.loadAll().size())));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Doctor could not read the plugin's data files", e);
        }
        for (WorldInfo world : worlds) {
            findings.add(new Finding(MessageKey.DOCTOR_BORDER, false, Arg.text("world", world.name()),
                    Arg.decimal("radius", world.borderRadius())));
            findings.add(new Finding(MessageKey.DOCTOR_DISK, false, Arg.text("world", world.name()),
                    Arg.text("free", ByteSize.format(freeSpace(world.folder())))));
        }
        return findings;
    }

    private long freeSpace(Path folder) {
        try {
            return Files.getFileStore(folder).getUsableSpace();
        } catch (IOException e) {
            return -1;
        }
    }
}
