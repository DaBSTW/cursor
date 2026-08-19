package dev.bookreports.integration.punishment;

import java.util.Optional;
import org.bukkit.plugin.PluginManager;

/** Detects an installed punishment plugin in {@code onEnable} (SPECS.md §11) — LiteBans first, then AdvancedBan. */
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
        return Optional.empty();
    }
}
