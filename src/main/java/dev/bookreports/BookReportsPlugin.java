package dev.bookreports;

import dev.bookreports.api.BookReportsAPI;
import dev.bookreports.api.BookReportsApiImpl;
import dev.bookreports.book.BookBuilder;
import dev.bookreports.chat.ChatContextTracker;
import dev.bookreports.command.BasicCommandAdapter;
import dev.bookreports.command.RateLimiter;
import dev.bookreports.command.ReportAdminCommand;
import dev.bookreports.command.ReportCommand;
import dev.bookreports.command.ReportToolListener;
import dev.bookreports.command.ReportsReloadCommand;
import dev.bookreports.command.StaffNotificationService;
import dev.bookreports.command.internal.SelectOptionCommand;
import dev.bookreports.command.internal.SelectTargetCommand;
import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.ConfigManager;
import dev.bookreports.config.ConfigurationException;
import dev.bookreports.config.LocaleManager;
import dev.bookreports.gui.AnvilInputGUI;
import dev.bookreports.integration.coreprotect.CoreProtectBridge;
import dev.bookreports.integration.coreprotect.CoreProtectBridges;
import dev.bookreports.integration.discord.DiscordNotifier;
import dev.bookreports.integration.metrics.BStatsMetrics;
import dev.bookreports.integration.placeholder.PlaceholderExpansionImpl;
import dev.bookreports.integration.proxy.ProxySyncChannel;
import dev.bookreports.integration.punishment.PunishmentBridge;
import dev.bookreports.integration.punishment.PunishmentBridges;
import dev.bookreports.service.CooldownService;
import dev.bookreports.service.DailyLimitService;
import dev.bookreports.service.PriorityCalculator;
import dev.bookreports.service.ReportService;
import dev.bookreports.session.SessionManager;
import dev.bookreports.storage.StorageException;
import dev.bookreports.storage.StorageManager;
import dev.bookreports.storage.dao.JdbcPenaltyDao;
import dev.bookreports.storage.dao.JdbcReportDao;
import dev.bookreports.storage.dao.JdbcStaffPrefsDao;
import dev.bookreports.storage.dao.PenaltyDao;
import dev.bookreports.storage.dao.ReportDao;
import dev.bookreports.storage.dao.StaffPrefsDao;
import dev.bookreports.util.BukkitSchedulerAdapter;
import dev.bookreports.util.FoliaDetector;
import dev.bookreports.util.FoliaSchedulerAdapter;
import dev.bookreports.util.SchedulerAdapter;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.sql.DataSource;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class BookReportsPlugin extends JavaPlugin {

    private static final long CLAIM_RELEASE_INTERVAL_TICKS = 20L * 60; // once a minute

    private SchedulerAdapter scheduler;
    private ConfigManager configManager;
    private LocaleManager localeManager;
    private StorageManager storageManager;
    private ExecutorService workerExecutor;
    private SessionManager sessionManager;
    private ChatContextTracker chatContextTracker;
    private AnvilInputGUI anvilInputGUI;
    private volatile DataSource dataSource;
    private volatile ReportService reportService;

    private BasicCommandAdapter reportCommandAdapter;
    private BasicCommandAdapter reportAdminCommandAdapter;
    private BasicCommandAdapter reportsReloadCommandAdapter;
    private BasicCommandAdapter selectCommandAdapter;
    private BasicCommandAdapter targetCommandAdapter;

    @Override
    public void onEnable() {
        scheduler = FoliaDetector.isFolia() ? new FoliaSchedulerAdapter(this) : new BukkitSchedulerAdapter(this);
        workerExecutor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "BookReports-Worker");
            thread.setDaemon(true);
            return thread;
        });

        configManager = new ConfigManager(this);
        localeManager = new LocaleManager(this);
        if (!loadConfigAndLocale()) {
            return;
        }

        registerCommands();
        reportsReloadCommandAdapter.bind(new ReportsReloadCommand(configManager, localeManager, getLogger()));

        sessionManager = new SessionManager(configManager::current, Clock.systemUTC());
        chatContextTracker = new ChatContextTracker();
        getServer().getPluginManager().registerEvents(chatContextTracker, this);
        BStatsMetrics.start(this, configManager::current);
        connectStorage();

        getLogger().info("BookReports v" + getPluginMeta().getVersion() + " enabled ("
                + (FoliaDetector.isFolia() ? "Folia" : "Bukkit") + " scheduler).");
    }

    @Override
    public void onDisable() {
        if (storageManager != null) {
            storageManager.close();
        }
        shutdownWorkerExecutor();
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        getLogger().info("BookReports disabled.");
    }

    private void shutdownWorkerExecutor() {
        if (workerExecutor == null) {
            return;
        }
        workerExecutor.shutdown();
        try {
            if (!workerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                workerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Registers every command via Paper's Brigadier lifecycle event — paper-plugin.yml's YAML {@code commands:} block
     * is unsupported since Paper 26.x (see {@link BasicCommandAdapter}). This must run synchronously here in
     * {@code onEnable}: {@code LifecycleEvents.COMMANDS} fires once, early in startup, well before storage connects
     * asynchronously and the real executors — most of which need {@link #reportService} — become available in
     * {@link #startServices}. The adapters are bound later; see {@link #registerBookFlow} and
     * {@link #registerStaffPanel}.
     */
    private void registerCommands() {
        reportCommandAdapter = new BasicCommandAdapter("report", "bookreports.report");
        reportAdminCommandAdapter = new BasicCommandAdapter("reportadmin", "bookreports.staff");
        reportsReloadCommandAdapter = new BasicCommandAdapter("reportsreload", "bookreports.admin");
        // Named distinctively rather than "select"/"target": Paper plugins no longer get a custom
        // fallback-namespace prefix to hide behind (see ComponentUtil), so the label itself has to be the
        // thing that avoids colliding with some other plugin's generic command name.
        selectCommandAdapter = new BasicCommandAdapter("bookreports-select", "bookreports.report");
        targetCommandAdapter = new BasicCommandAdapter("bookreports-target", "bookreports.report");

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register("report", "Open the report book.", List.of(), reportCommandAdapter);
            commands.register("reportadmin", "Open the staff report review queue.", List.of("rvw", "reports"),
                    reportAdminCommandAdapter);
            commands.register("reportsreload", "Reload BookReports configuration and locale files.", List.of(),
                    reportsReloadCommandAdapter);
            commands.register("bookreports-select", "Internal book click handler. Not for manual use.", List.of(),
                    selectCommandAdapter);
            commands.register("bookreports-target",
                    "Internal book click handler for the online-player picker. Not for manual use.", List.of(),
                    targetCommandAdapter);
        });
    }

    private boolean loadConfigAndLocale() {
        try {
            BookReportsConfig config = configManager.load();
            localeManager.load(config.locale());
            return true;
        } catch (ConfigurationException e) {
            getLogger().log(Level.SEVERE, "Disabling BookReports: invalid config.yml", e);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    private void connectStorage() {
        storageManager = new StorageManager(getLogger());
        BookReportsConfig config = configManager.current();
        scheduler.runAsync(() -> {
            try {
                dataSource = storageManager.connect(config.storageType(), config.mysql(), getDataFolder());
                getLogger().info("Storage connected: type=" + config.storageType());
                scheduler.runGlobal(this::startServices);
            } catch (StorageException e) {
                getLogger().log(Level.SEVERE, "Disabling BookReports: storage connection failed", e);
                scheduler.runGlobal(() -> getServer().getPluginManager().disablePlugin(this));
            }
        });
    }

    private void startServices() {
        ReportDao reportDao = new JdbcReportDao(dataSource);
        PenaltyDao penaltyDao = new JdbcPenaltyDao(dataSource);
        StaffPrefsDao staffPrefsDao = new JdbcStaffPrefsDao(dataSource);
        Clock clock = Clock.systemUTC();

        CooldownService cooldownService = new CooldownService(clock);
        DailyLimitService dailyLimitService = new DailyLimitService(reportDao, configManager::current, clock);
        PriorityCalculator priorityCalculator = new PriorityCalculator(configManager::current, clock);

        var coreProtectSettings = configManager.current().coreProtect();
        Optional<CoreProtectBridge> coreProtectBridge = CoreProtectBridges.detect(getServer().getPluginManager(),
                coreProtectSettings.lookbackSeconds(), coreProtectSettings.maxEntries());
        coreProtectBridge.ifPresent(bridge -> getLogger().info("CoreProtect bridge active."));

        reportService = new ReportService(reportDao, penaltyDao, cooldownService, dailyLimitService, priorityCalculator,
                configManager::current, getServer().getPluginManager(), scheduler, workerExecutor, clock, getLogger(),
                coreProtectBridge);

        BookReportsAPI api = new BookReportsApiImpl(reportService, configManager::current, workerExecutor);
        getServer().getServicesManager().register(BookReportsAPI.class, api, this, ServicePriority.Normal);
        getLogger().info("BookReportsAPI registered with the services manager.");

        anvilInputGUI = new AnvilInputGUI(localeManager);
        getServer().getPluginManager().registerEvents(anvilInputGUI, this);

        registerBookFlow();
        registerStaffPanel(reportDao, staffPrefsDao);
        registerIntegrations(reportDao, cooldownService);
        releaseStaleClaimsLoop();
    }

    /** Registered only once storage/{@link #reportService} are ready — every book command ends in a submit. */
    private void registerBookFlow() {
        BookBuilder books = new BookBuilder(localeManager);
        RateLimiter rateLimiter = new RateLimiter(Duration.ofSeconds(1));
        getServer().getPluginManager().registerEvents(
                new ReportToolListener(configManager::current, sessionManager, localeManager, books, rateLimiter),
                this);

        reportCommandAdapter.bind(new ReportCommand(sessionManager, configManager::current, localeManager, books,
                rateLimiter, reportService, scheduler));
        targetCommandAdapter.bind(new SelectTargetCommand(sessionManager, localeManager, books));
        selectCommandAdapter.bind(new SelectOptionCommand(sessionManager, configManager::current, localeManager, books,
                reportService, anvilInputGUI, chatContextTracker, scheduler, getLogger()));
    }

    private void registerStaffPanel(ReportDao reportDao, StaffPrefsDao staffPrefsDao) {
        StaffNotificationService notifications = new StaffNotificationService(configManager::current, localeManager,
                staffPrefsDao, workerExecutor, getLogger());
        getServer().getPluginManager().registerEvents(notifications, this);
        Optional<PunishmentBridge> punishmentBridge = PunishmentBridges.detect(getServer().getPluginManager());
        punishmentBridge.ifPresent(bridge -> getLogger().info("Punishment bridge active: " + bridge.name()));
        reportAdminCommandAdapter.bind(new ReportAdminCommand(this, localeManager, reportService, reportDao,
                configManager::current, notifications, punishmentBridge, workerExecutor, anvilInputGUI));
    }

    /** Every integration here is soft-depend (SPECS.md §11/§6) — absent, it's simply never registered. */
    private void registerIntegrations(ReportDao reportDao, CooldownService cooldownService) {
        DiscordNotifier discordNotifier = new DiscordNotifier(configManager::current, workerExecutor, getLogger());
        getServer().getPluginManager().registerEvents(discordNotifier, this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null
                && configManager.current().placeholderApiEnabled()) {
            new PlaceholderExpansionImpl(reportDao, cooldownService, workerExecutor, getPluginMeta().getVersion())
                    .register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        ProxySyncChannel proxySync = new ProxySyncChannel(this, getLogger(),
                remote -> getLogger().info("Remote report #" + remote.id() + " (" + remote.priority() + ") on server '"
                        + remote.originServer() + "': " + remote.targetName() + " — " + remote.categoryId()));
        proxySync.register();
        getServer().getPluginManager().registerEvents(proxySync, this);
    }

    /** Runs once immediately, then reschedules itself every {@link #CLAIM_RELEASE_INTERVAL_TICKS}. */
    private void releaseStaleClaimsLoop() {
        reportService.releaseStaleClaims().whenComplete((released, error) -> {
            if (error != null) {
                getLogger().log(Level.WARNING, "Failed to release stale claims", error);
            } else if (released > 0) {
                getLogger().info("Auto-released " + released + " stale claim(s).");
            }
        });
        scheduler.runGlobalLater(this::releaseStaleClaimsLoop, CLAIM_RELEASE_INTERVAL_TICKS);
    }

    public SchedulerAdapter scheduler() {
        return scheduler;
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public LocaleManager localeManager() {
        return localeManager;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public ReportService reportService() {
        return reportService;
    }
}
