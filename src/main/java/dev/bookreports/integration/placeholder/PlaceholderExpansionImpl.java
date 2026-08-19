package dev.bookreports.integration.placeholder;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.bookreports.service.CooldownService;
import dev.bookreports.storage.dao.ReportDao;
import dev.bookreports.storage.model.ReportStatus;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI expansion (SPECS.md §11): {@code %bookreports_pending_count%}, {@code %bookreports_my_cooldown%},
 * {@code %bookreports_target_report_count%}.
 *
 * <p>
 * PAPI can call {@link #onPlaceholderRequest} from any thread, so every value here is served from an in-memory cache
 * and refreshed asynchronously in the background rather than blocking on the DB.
 */
public final class PlaceholderExpansionImpl extends PlaceholderExpansion {

    private final ReportDao reportDao;
    private final CooldownService cooldownService;
    private final Executor executor;
    private final String version;
    private final AtomicInteger pendingCount = new AtomicInteger();
    private final AtomicBoolean refreshingPendingCount = new AtomicBoolean();
    private final Cache<UUID, Integer> targetReportCounts = Caffeine.newBuilder().maximumSize(2_000)
            .expireAfterWrite(Duration.ofSeconds(30)).build();

    public PlaceholderExpansionImpl(ReportDao reportDao, CooldownService cooldownService, Executor executor,
            String version) {
        this.reportDao = Objects.requireNonNull(reportDao, "reportDao");
        this.cooldownService = Objects.requireNonNull(cooldownService, "cooldownService");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.version = Objects.requireNonNull(version, "version");
    }

    @Override
    public String getIdentifier() {
        return "bookreports";
    }

    @Override
    public String getAuthor() {
        return "DaBSTW";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "pending_count" -> String.valueOf(pendingCountRefreshingIfStale());
            case "my_cooldown" -> String.valueOf(
                    cooldownService.remainingCooldown(player.getUniqueId()).map(Duration::toSeconds).orElse(0L));
            case "target_report_count" -> String.valueOf(targetReportCountRefreshingIfStale(player.getUniqueId()));
            default -> null;
        };
    }

    private int pendingCountRefreshingIfStale() {
        if (refreshingPendingCount.compareAndSet(false, true)) {
            executor.execute(() -> {
                try {
                    pendingCount.set(reportDao.countByStatus(ReportStatus.PENDING));
                } finally {
                    refreshingPendingCount.set(false);
                }
            });
        }
        return pendingCount.get();
    }

    private int targetReportCountRefreshingIfStale(UUID targetUuid) {
        Integer cached = targetReportCounts.getIfPresent(targetUuid);
        if (cached != null) {
            return cached;
        }
        executor.execute(() -> targetReportCounts.put(targetUuid, reportDao.findByTarget(targetUuid).size()));
        return 0;
    }
}
