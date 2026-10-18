package dev.pregen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.pregen.api.PregenApi;
import dev.pregen.command.CommandSupport;
import dev.pregen.command.ConfirmationService;
import dev.pregen.command.PluginControl;
import dev.pregen.command.PregenCommand;
import dev.pregen.config.PluginSettings;
import dev.pregen.config.SettingsHolder;
import dev.pregen.config.SettingsLoader;
import dev.pregen.diagnostics.Doctor;
import dev.pregen.generation.GenerationService;
import dev.pregen.generation.PaperTickProbe;
import dev.pregen.generation.PregenApiImpl;
import dev.pregen.generation.TaskLauncher;
import dev.pregen.generation.ThrottleController;
import dev.pregen.i18n.LanguageFiles;
import dev.pregen.i18n.MessageKey;
import dev.pregen.i18n.MessageService;
import dev.pregen.listener.SessionListener;
import dev.pregen.persistence.AsyncTaskStore;
import dev.pregen.persistence.JsonTaskStore;
import dev.pregen.progress.BossBarDisplay;
import dev.pregen.progress.Notifier;
import dev.pregen.progress.ProgressReporter;
import dev.pregen.selection.SelectionRegistry;
import dev.pregen.trim.PendingTrimStore;
import dev.pregen.trim.TrimService;
import dev.pregen.util.WorkerPools;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point. Wires the services together and owns their lifecycle; all behaviour lives in the services.
 *
 * <p>Loaded at {@code STARTUP}, before worlds exist, so that scheduled trims can modify region files while nothing has
 * them open. Saved tasks are resumed on the first server tick, once worlds are loaded.
 */
public final class PregenPlugin extends JavaPlugin implements PluginControl {

    private static final long SHUTDOWN_TIMEOUT_MILLIS = 5_000;

    private final Object configLock = new Object();
    private SettingsHolder settings;
    private MessageService messages;
    private WorkerPools pools;
    private ThrottleController throttle;
    private GenerationService generation;
    private BossBarDisplay bossBars;
    private SelectionRegistry selections;
    private ScheduledTask heartbeat;

    @Override
    public void onEnable() {
        Path dataFolder = getDataFolder().toPath();
        PluginSettings initial = loadSettings();
        settings = new SettingsHolder(initial);
        messages = new MessageService(loadLanguages(dataFolder), initial.language(), initial.useClientLocale());
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        pools = new WorkerPools(initial.generation().workerThreads(), getLogger());

        PendingTrimStore trimStore = new PendingTrimStore(dataFolder.resolve("pending-trims.json"), gson);
        JsonTaskStore taskStore = new JsonTaskStore(dataFolder.resolve("tasks"), gson, getLogger()::warning);
        AsyncTaskStore asyncStore = new AsyncTaskStore(taskStore, pools.io(), getLogger());
        Notifier notifier = new Notifier(getServer(), messages);
        throttle = new ThrottleController(initial, new PaperTickProbe(getServer()),
                () -> getServer().getOnlinePlayers().size());
        TaskLauncher launcher = new TaskLauncher(getServer(), settings, taskStore,
                world -> hasPendingTrim(trimStore, world), dataFolder.resolve("csv"), pools.io(), getLogger());
        generation = new GenerationService(this, settings, throttle, launcher, asyncStore, pools, notifier);
        TrimService trims = new TrimService(getServer(), trimStore, settings, generation, dataFolder, pools.io(),
                getLogger());
        trims.runPendingAtStartup();

        bossBars = new BossBarDisplay(getServer(), messages, settings);
        ProgressReporter reporter = new ProgressReporter(generation, settings, notifier, bossBars,
                getServer().getPluginManager());
        selections = new SelectionRegistry();
        ConfirmationService confirmations = new ConfirmationService();
        Doctor doctor = new Doctor(Path.of("server.properties"), generation, taskStore, trimStore, getLogger());

        getServer().getPluginManager()
                .registerEvents(new SessionListener(selections, confirmations, bossBars, generation), this);
        registerCommands(
                new PregenCommand(new CommandSupport(this, messages, settings, generation, selections, confirmations),
                        bossBars, trims, doctor, this,
                        new PregenCommand.Paths(dataFolder.resolve("csv"), Path.of("server.properties")), pools.io()));
        getServer().getServicesManager().register(PregenApi.class, new PregenApiImpl(generation), this,
                ServicePriority.Normal);

        heartbeat = getServer().getAsyncScheduler().runAtFixedRate(this, task -> beat(reporter), 1, 1,
                TimeUnit.SECONDS);
        getServer().getGlobalRegionScheduler().run(this, task -> generation.resumeSavedTasksOnStartup());
    }

    @Override
    public void onDisable() {
        if (heartbeat != null) {
            heartbeat.cancel();
        }
        if (generation != null) {
            generation.shutdown(SHUTDOWN_TIMEOUT_MILLIS);
        }
        if (bossBars != null) {
            bossBars.clear();
        }
        if (selections != null) {
            selections.clear();
        }
        getServer().getServicesManager().unregisterAll(this);
        HandlerList.unregisterAll(this);
        if (pools != null) {
            pools.shutdown(SHUTDOWN_TIMEOUT_MILLIS);
        }
    }

    @Override
    public void persistSetting(String path, Object value) {
        pools.io().execute(() -> {
            synchronized (configLock) {
                getConfig().set(path, value);
                saveConfig();
            }
        });
    }

    @Override
    public CompletableFuture<Void> reload() {
        return CompletableFuture.runAsync(() -> {
            PluginSettings reloaded;
            synchronized (configLock) {
                reloadConfig();
                reloaded = loadSettings();
            }
            settings.set(reloaded);
            throttle.updateSettings(reloaded);
            messages.reload(loadLanguages(getDataFolder().toPath()), reloaded.language(), reloaded.useClientLocale());
            getLogger().info("Configuration reloaded: language=" + reloaded.language() + " maxConcurrent="
                    + reloaded.generation().maxConcurrentChunks());
        }, pools.io());
    }

    private void beat(ProgressReporter reporter) {
        try {
            generation.heartbeat();
            reporter.heartbeat();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Heartbeat failed; it will run again in a second", e);
        }
    }

    private void registerCommands(PregenCommand command) {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar()
                .register(command.build(), "Pre-generate and trim chunks", PregenCommand.ALIASES));
    }

    private PluginSettings loadSettings() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        PluginSettings loaded = SettingsLoader.load(getConfig(), getLogger()::warning);
        // Adds keys introduced by newer versions to the existing file while keeping the user's values.
        saveConfig();
        return loaded;
    }

    private Map<String, Map<MessageKey, String>> loadLanguages(Path dataFolder) {
        Path langDirectory = dataFolder.resolve("lang");
        for (String language : SettingsLoader.LANGUAGES) {
            if (!Files.exists(langDirectory.resolve(language + ".yml"))) {
                saveResource("lang/" + language + ".yml", false);
            }
        }
        return LanguageFiles.load(langDirectory, SettingsLoader.LANGUAGES, this::getResource, getLogger()::warning);
    }

    private boolean hasPendingTrim(PendingTrimStore store, String world) {
        try {
            return store.find(world).isPresent();
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Cannot read pending trims; assuming none for world=" + world, e);
            return false;
        }
    }
}
