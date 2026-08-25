package dev.bookreports.integration.vault;

import java.util.Optional;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Detects an installed Vault plus a registered {@code Chat} provider (SPECS.md §11-style optional integration) —
 * mirrors {@link dev.bookreports.integration.punishment.PunishmentBridges}' detect-in-{@code onEnable} pattern. Unlike
 * the punishment/CoreProtect bridges, Vault itself is a thin API shim: the actual prefix/suffix data comes from
 * whichever permission plugin (LuckPerms, etc.) registered a {@code Chat} service with Bukkit's services manager, so
 * both checks are needed — Vault present but no provider registered means no permission plugin actually hooked in.
 */
public final class VaultBridges {

    private VaultBridges() {
    }

    public static Optional<VaultBridge> detect(PluginManager pluginManager) {
        if (pluginManager.getPlugin("Vault") == null) {
            return Optional.empty();
        }
        RegisteredServiceProvider<Chat> registration = Bukkit.getServicesManager().getRegistration(Chat.class);
        if (registration == null) {
            return Optional.empty();
        }
        return Optional.of(new VaultBridgeImpl(registration.getProvider()));
    }
}
