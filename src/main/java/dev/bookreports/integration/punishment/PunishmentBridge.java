package dev.bookreports.integration.punishment;

/**
 * Hook to an external punishment plugin (SPECS.md §11), used by the staff panel's quick-sanction buttons. All methods
 * must be called from the main thread — implementations dispatch console commands, which Bukkit only allows there.
 */
public interface PunishmentBridge {

    /** Display name shown in staff-facing messages, e.g. {@code "LiteBans"}. */
    String name();

    void ban(String playerName, String duration, String reason, String staffName);

    void mute(String playerName, String duration, String reason, String staffName);

    void kick(String playerName, String reason, String staffName);
}
