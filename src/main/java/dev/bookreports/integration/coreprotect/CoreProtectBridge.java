package dev.bookreports.integration.coreprotect;

import java.util.UUID;

/**
 * Hook to an installed CoreProtect for automatic evidence (SPECS.md §11-style optional integration), same spirit as
 * {@link dev.bookreports.chat.ChatContextTracker}: attach what CoreProtect already logged about the target, without the
 * reporter having to ask staff to go look it up.
 */
public interface CoreProtectBridge {

    /**
     * A short, human-readable summary of the target's most recent CoreProtect-logged block actions (e.g.
     * {@code "3x break, 1x place (last 5m)"}), or {@code null} if CoreProtect has nothing on file for them in the
     * configured lookback window.
     */
    String recentActivity(UUID targetUuid, String targetName);
}
