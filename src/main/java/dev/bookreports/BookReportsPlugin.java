package dev.bookreports;

import dev.bookreports.command.ReportsReloadCommand;
import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.ConfigManager;
import dev.bookreports.config.ConfigurationException;
import dev.bookreports.config.LocaleManager;
import dev.bookreports.storage.StorageException;
import dev.bookreports.storage.StorageManager;
import dev.bookreports.util.BukkitSchedulerAdapter;
import dev.bookreports.util.FoliaDetector;
import dev.bookreports.util.FoliaSchedulerAdapter;
import dev.bookreports.util.SchedulerAdapter;
import java.util.logging.Level;
import javax.sql.DataSource;
import org.bukkit.plugin.java.JavaPlugin;

public final class BookReportsPlugin extends JavaPlugin {

    private SchedulerAdapter scheduler;
    private ConfigManager configManager;
    private LocaleManager localeManager;
    private StorageManager storageManager;
    private volatile DataSource dataSource;

    @Override
    public void onEnable() {
        scheduler = FoliaDetector.isFolia() ? new FoliaSchedulerAdapter(this) : new BukkitSchedulerAdapter(this);

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
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        getLogger().info("BookReports disabled.");
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
            } catch (StorageException e) {
                getLogger().log(Level.SEVERE, "Disabling BookReports: storage connection failed", e);
                scheduler.runGlobal(() -> getServer().getPluginManager().disablePlugin(this));
            }
        });
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
}
