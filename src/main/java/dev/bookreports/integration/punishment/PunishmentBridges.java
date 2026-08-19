package dev.bookreports.integration.punishment;

import java.util.Optional;
import org.bukkit.plugin.PluginManager;

/**
 * Detects an installed punishment plugin in {@code onEnable} (SPECS.md §11) — LiteBans and AdvancedBan are dedicated
 * punishment plugins with proper temp-ban tracking, so they're preferred; EssentialsX is checked last since punishments
 * are only a small part of what it does.
 */
public final class PunishmentBridges {

    private PunishmentBridges() {
    }

    public static Optional<PunishmentBridge> detect(PluginManager pluginManager) {
        if (pluginManager.getPlugin("LiteBans") != null) {
            return Optional.of(new LiteBansBridge());
        }
        if (pluginManager.getPlugin("AdvancedBan") != null) {
            return Optional.of(new AdvancedBanBridge());
        }
        if (pluginManager.getPlugin("Essentials") != null) {
            return Optional.of(new EssentialsBridge());
        }
        return Optional.empty();
    }
}
