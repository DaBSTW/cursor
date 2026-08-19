package dev.bookreports;

import dev.bookreports.util.BukkitSchedulerAdapter;
import dev.bookreports.util.FoliaDetector;
import dev.bookreports.util.FoliaSchedulerAdapter;
import dev.bookreports.util.SchedulerAdapter;
import org.bukkit.plugin.java.JavaPlugin;

public final class BookReportsPlugin extends JavaPlugin {

    private SchedulerAdapter scheduler;

    @Override
    public void onEnable() {
        scheduler = FoliaDetector.isFolia() ? new FoliaSchedulerAdapter(this) : new BukkitSchedulerAdapter(this);
        getLogger().info("BookReports v" + getPluginMeta().getVersion() + " enabled ("
                + (FoliaDetector.isFolia() ? "Folia" : "Bukkit") + " scheduler).");
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        getLogger().info("BookReports disabled.");
    }

    public SchedulerAdapter scheduler() {
        return scheduler;
    }
}
