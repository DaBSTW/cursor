package dev.bookreports.integration.coreprotect;

import dev.bookreports.util.TextSanitizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.coreprotect.CoreProtectAPI;

/**
 * Wraps a real {@link CoreProtectAPI} instance. Kept off the main thread by the caller ({@code ReportService} runs this
 * during draft-building, already inside its worker executor) — {@code performLookup} hits CoreProtect's own database,
 * same as our own {@code JdbcReportDao} does on a worker thread.
 */
final class CoreProtectBridgeImpl implements CoreProtectBridge {

    /** Leaves headroom under the {@code coreprotect_context VARCHAR(512)} column. */
    static final int MAX_LENGTH = 500;

    private final CoreProtectAPI api;
    private final int lookbackSeconds;
    private final int maxEntries;

    CoreProtectBridgeImpl(CoreProtectAPI api, int lookbackSeconds, int maxEntries) {
        this.api = Objects.requireNonNull(api, "api");
        this.lookbackSeconds = lookbackSeconds;
        this.maxEntries = maxEntries;
    }

    @Override
    public String recentActivity(UUID targetUuid, String targetName) {
        List<String[]> rows = api.performLookup(lookbackSeconds, List.of(targetName), null, null, null, null, 0, null);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        // Grouped by action (break/place/click/kill) rather than listed line by line — a raw log dump isn't
        // evidence a staff member can act on at a glance, a count is.
        Map<String, Integer> countsByAction = new LinkedHashMap<>();
        int scanned = 0;
        for (String[] row : rows) {
            if (scanned++ >= maxEntries) {
                break;
            }
            String action = api.parseResult(row).getActionString();
            countsByAction.merge(action, 1, Integer::sum);
        }
        if (countsByAction.isEmpty()) {
            return null;
        }
        StringBuilder summary = new StringBuilder();
        countsByAction.forEach((action, count) -> {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(count).append("x ").append(action);
        });
        summary.append(" (last ").append(formatLookback()).append(')');
        return TextSanitizer.stripAndTruncate(summary.toString(), MAX_LENGTH);
    }

    private String formatLookback() {
        if (lookbackSeconds % 60 == 0) {
            return (lookbackSeconds / 60) + "m";
        }
        return lookbackSeconds + "s";
    }
}
