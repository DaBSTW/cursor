package dev.bookreports;

import dev.bookreports.api.BookReportsAPI;
import dev.bookreports.api.BookReportsApiImpl;
import dev.bookreports.command.ReportsReloadCommand;
import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.ConfigManager;
import dev.bookreports.config.ConfigurationException;
import dev.bookreports.config.LocaleManager;
import dev.bookreports.service.CooldownService;
import dev.bookreports.service.DailyLimitService;
import dev.bookreports.service.PriorityCalculator;
import dev.bookreports.service.ReportService;
import dev.bookreports.storage.StorageException;
import dev.bookreports.storage.StorageManager;
import dev.bookreports.storage.dao.JdbcPenaltyDao;
import dev.bookreports.storage.dao.JdbcReportDao;
import dev.bookreports.storage.dao.PenaltyDao;
import dev.bookreports.storage.dao.ReportDao;
import dev.bookreports.util.BukkitSchedulerAdapter;
import dev.bookreports.util.FoliaDetector;
import dev.bookreports.util.FoliaSchedulerAdapter;
import dev.bookreports.util.SchedulerAdapter;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.sql.DataSource;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class BookReportsPlugin extends JavaPlugin {

    private SchedulerAdapter scheduler;
    private ConfigManager configManager;
    private LocaleManager localeManager;
    private StorageManager storageManager;
    private ExecutorService workerExecutor;
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
        Clock clock = Clock.systemUTC();

        CooldownService cooldownService = new CooldownService(clock);
        DailyLimitService dailyLimitService = new DailyLimitService(reportDao, configManager::current, clock);
        PriorityCalculator priorityCalculator = new PriorityCalculator(configManager::current, clock);

        reportService = new ReportService(reportDao, penaltyDao, cooldownService, dailyLimitService, priorityCalculator,
                configManager::current, getServer().getPluginManager(), scheduler, workerExecutor, clock, getLogger());

        BookReportsAPI api = new BookReportsApiImpl(reportService, configManager::current, workerExecutor);
        getServer().getServicesManager().register(BookReportsAPI.class, api, this, ServicePriority.Normal);
        getLogger().info("BookReportsAPI registered with the services manager.");
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
