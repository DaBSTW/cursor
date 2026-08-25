package dev.bookreports.integration.vault;

import java.util.Objects;
import java.util.function.Supplier;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.OfflinePlayer;

final class VaultBridgeImpl implements VaultBridge {

    private final Chat chat;

    VaultBridgeImpl(Chat chat) {
        this.chat = Objects.requireNonNull(chat, "chat");
    }

    @Override
    public String prefix(OfflinePlayer player) {
        return safeCall(() -> chat.getPlayerPrefix(null, player));
    }

    @Override
    public String suffix(OfflinePlayer player) {
        return safeCall(() -> chat.getPlayerSuffix(null, player));
    }

    /**
     * Chat implementations vary widely (LuckPerms, PermissionsEx, GroupManager, ...) in how they handle a {@code null}
     * world or an offline player — some throw, some just return {@code null}. This is purely cosmetic, so any failure
     * quietly becomes "no prefix/suffix" rather than breaking the queue or detail view it decorates.
     */
    private String safeCall(Supplier<String> call) {
        try {
            String value = call.get();
            return value != null ? value : "";
        } catch (RuntimeException e) {
            return "";
        }
    }
}
