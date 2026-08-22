package dev.bookreports.integration.coreprotect;

import java.util.Optional;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Detects an installed, ready CoreProtect (SPECS.md §11-style optional integration) — mirrors
 * {@link dev.bookreports.integration.punishment.PunishmentBridges}' detect-in-{@code onEnable} pattern.
 */
public final class CoreProtectBridges {

    /** The lowest {@code CoreProtectAPI#APIVersion()} this bridge was written against. */
    private static final int MIN_API_VERSION = 9;

    private CoreProtectBridges() {
    }

    public static Optional<CoreProtectBridge> detect(PluginManager pluginManager, int lookbackSeconds, int maxEntries) {
        Plugin plugin = pluginManager.getPlugin("CoreProtect");
        if (!(plugin instanceof CoreProtect coreProtect) || !plugin.isEnabled()) {
            return Optional.empty();
        }
        CoreProtectAPI api = coreProtect.getAPI();
        if (api == null || !api.isEnabled() || api.APIVersion() < MIN_API_VERSION) {
            return Optional.empty();
        }
        return Optional.of(new CoreProtectBridgeImpl(api, lookbackSeconds, maxEntries));
    }
}
