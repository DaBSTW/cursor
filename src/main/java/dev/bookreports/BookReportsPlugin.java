package dev.bookreports;

import dev.bookreports.api.BookReportsAPI;
import dev.bookreports.api.BookReportsApiImpl;
import dev.bookreports.book.BookBuilder;
import dev.bookreports.chat.ChatContextTracker;
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
import dev.bookreports.integration.discord.DiscordNotifier;
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
import java.time.Clock;
import java.time.Duration;
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
    private volatile DataSource dataSource;
    private volatile ReportService reportService;

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

        var reloadCommand = new ReportsReloadCommand(configManager, localeManager, getLogger());
        var reportsReload = getCommand("reportsreload");
        if (reportsReload != null) {
            reportsReload.setExecutor(reloadCommand);
        }

        sessionManager = new SessionManager(configManager::current, Clock.systemUTC());
        chatContextTracker = new ChatContextTracker();
        getServer().getPluginManager().registerEvents(chatContextTracker, this);
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

        reportService = new ReportService(reportDao, penaltyDao, cooldownService, dailyLimitService, priorityCalculator,
                configManager::current, getServer().getPluginManager(), scheduler, workerExecutor, clock, getLogger());

        BookReportsAPI api = new BookReportsApiImpl(reportService, configManager::current, workerExecutor);
        getServer().getServicesManager().register(BookReportsAPI.class, api, this, ServicePriority.Normal);
        getLogger().info("BookReportsAPI registered with the services manager.");

        registerBookFlow();
        registerStaffPanel(reportDao, staffPrefsDao);
        registerIntegrations(reportDao, cooldownService);
        releaseStaleClaimsLoop();
    }

    /** Registered only once storage/{@link #reportService} are ready — every book command ends in a submit. */
    private void registerBookFlow() {
        BookBuilder books = new BookBuilder(localeManager);
        RateLimiter rateLimiter = new RateLimiter(Duration.ofSeconds(1));
        AnvilInputGUI anvilInputGUI = new AnvilInputGUI(localeManager);
        getServer().getPluginManager().registerEvents(anvilInputGUI, this);
        getServer().getPluginManager().registerEvents(
                new ReportToolListener(configManager::current, sessionManager, localeManager, books, rateLimiter),
                this);

        setExecutorIfPresent("report",
                new ReportCommand(sessionManager, configManager::current, localeManager, books, rateLimiter));
        setExecutorIfPresent("target", new SelectTargetCommand(sessionManager, localeManager, books));
        setExecutorIfPresent("select", new SelectOptionCommand(sessionManager, configManager::current, localeManager,
                books, reportService, anvilInputGUI, chatContextTracker, scheduler, getLogger()));
    }

    private void registerStaffPanel(ReportDao reportDao, StaffPrefsDao staffPrefsDao) {
        StaffNotificationService notifications = new StaffNotificationService(configManager::current, localeManager,
                staffPrefsDao, workerExecutor, getLogger());
        getServer().getPluginManager().registerEvents(notifications, this);
        Optional<PunishmentBridge> punishmentBridge = PunishmentBridges.detect(getServer().getPluginManager());
        punishmentBridge.ifPresent(bridge -> getLogger().info("Punishment bridge active: " + bridge.name()));
        setExecutorIfPresent("reportadmin", new ReportAdminCommand(this, localeManager, reportService, reportDao,
                configManager::current, notifications, punishmentBridge, workerExecutor));
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

    private void setExecutorIfPresent(String name, org.bukkit.command.CommandExecutor executor) {
        var command = getCommand(name);
        if (command == null) {
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
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
