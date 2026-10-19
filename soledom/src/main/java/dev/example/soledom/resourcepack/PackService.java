package dev.example.soledom.resourcepack;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.example.soledom.api.ModelDefinition;
import dev.example.soledom.api.PartDefinition;
import dev.example.soledom.compat.VersionAdapter;
import dev.example.soledom.core.config.Messages;
import dev.example.soledom.core.config.PackSettings;
import dev.example.soledom.core.config.SoledomConfig;
import dev.example.soledom.core.utils.RuntimeValidator;
import dev.example.soledom.registry.ModelRegistry;
import dev.example.soledom.registry.RegisteredModel;
import dev.example.soledom.scheduler.SchedulerAdapter;

/** Glue between the pure pack generator and the server: async export, hashing, and sending packs to players. */
public final class PackService {

    public static final String EXPORT_DIR = "export";
    public static final String OVERLAY_DIR = "pack";

    private final Path dataFolder;
    private final ModelRegistry registry;
    private final SchedulerAdapter scheduler;
    private final VersionAdapter adapter;
    private final Supplier<SoledomConfig> config;
    private final Logger logger;
    private final AtomicBoolean exporting = new AtomicBoolean();

    public PackService(@NotNull Path dataFolder, @NotNull ModelRegistry registry, @NotNull SchedulerAdapter scheduler,
            @NotNull VersionAdapter adapter, @NotNull Supplier<SoledomConfig> config, @NotNull Logger logger) {
        this.dataFolder = dataFolder;
        this.registry = registry;
        this.scheduler = scheduler;
        this.adapter = adapter;
        this.config = config;
        this.logger = logger;
    }

    public @NotNull Path zipPath() {
        return dataFolder.resolve(EXPORT_DIR).resolve(config.get().pack().fileName());
    }

    /**
     * Generates and writes the pack on an async thread.
     *
     * @return the result, or a future failing with {@link IllegalStateException} if an export is already running
     */
    public @NotNull CompletableFuture<PackExporter.PackResult> export() {
        if (!exporting.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("an export is already running"));
        }
        // Snapshot everything that touches server state on the calling thread.
        List<ModelDefinition> models = registry.all().stream().map(RegisteredModel::definition).toList();
        Set<String> blockItems = new HashSet<>();
        for (ModelDefinition model : models) {
            for (PartDefinition part : model.parts()) {
                if (part.material() != null && RuntimeValidator.isBlockItem(part.material())) {
                    blockItems.add(part.material());
                }
            }
        }
        PackSettings settings = config.get().pack();
        PackMeta meta = new PackMeta(settings.description(), settings.packFormat(), settings.minFormat(),
                settings.maxFormat());
        Path zip = zipPath();

        CompletableFuture<PackExporter.PackResult> future = new CompletableFuture<>();
        scheduler.runAsync(() -> {
            try {
                PackGenerator.GeneratedPack pack = new PackGenerator(blockItems::contains)
                        .generate(models, meta, dataFolder.resolve(OVERLAY_DIR));
                PackExporter.PackResult result = new PackExporter().export(pack, zip);
                logger.info("Resource pack exported to " + result.zip() + " (" + result.entries() + " files, SHA-1 "
                        + result.sha1() + ")");
                result.warnings().forEach(w -> logger.warning("Pack: " + w));
                future.complete(result);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Resource pack export failed", e);
                future.completeExceptionally(e);
            } finally {
                exporting.set(false);
            }
        });
        return future;
    }

    /** Sends the configured pack, if a URL is set. @return false when no URL is configured */
    public boolean send(@NotNull Player player) {
        PackSettings settings = config.get().pack();
        if (settings.url().isBlank()) {
            return false;
        }
        adapter.sendResourcePack(player, settings.url().trim(), effectiveSha1(settings),
                settings.prompt().isBlank() ? null : Messages.parse(settings.prompt()), settings.required());
        return true;
    }

    public void sendOnJoin(@NotNull Player player) {
        if (config.get().pack().sendOnJoin()) {
            send(player);
        }
    }

    /** {@code resource-pack.sha1}, or the hash of the last export when allowed and no explicit hash is set. */
    public @Nullable String effectiveSha1(@NotNull PackSettings settings) {
        if (!settings.sha1().isBlank()) {
            return settings.sha1().trim();
        }
        return settings.useExportedHash() ? PackExporter.readExportedSha1(zipPath()) : null;
    }
}
