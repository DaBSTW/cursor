package dev.bookreports.integration.vault;

import org.bukkit.OfflinePlayer;

/**
 * Hook to Vault's {@code Chat} service (SPECS.md §11-style optional integration): shows a reporter's/target's rank
 * prefix and suffix next to their name in the staff queue and report detail view — purely cosmetic, never affects
 * anything else about the report. Safe to call for an offline player; most Vault-backed permission plugins (LuckPerms,
 * etc.) resolve prefixes from their own cache rather than requiring the player to be online.
 */
public interface VaultBridge {

    /** {@code ""} (never {@code null}) when the player has no prefix or the lookup fails. */
    String prefix(OfflinePlayer player);

    /** {@code ""} (never {@code null}) when the player has no suffix or the lookup fails. */
    String suffix(OfflinePlayer player);
}
