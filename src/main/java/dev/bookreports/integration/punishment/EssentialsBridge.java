package dev.bookreports.integration.punishment;

import org.bukkit.Bukkit;

/**
 * Dispatches EssentialsX's console commands, prefixed with its plugin namespace to dodge alias collisions. Bans go
 * through {@code tempban} since {@link PunishmentBridge#ban} always carries a duration.
 */
final class EssentialsBridge implements PunishmentBridge {

    @Override
    public String name() {
        return "Essentials";
    }

    @Override
    public void ban(String playerName, String duration, String reason, String staffName) {
        dispatch("essentials:tempban " + playerName + " " + duration + " " + reason);
    }

    @Override
    public void mute(String playerName, String duration, String reason, String staffName) {
        dispatch("essentials:mute " + playerName + " " + duration + " " + reason);
    }

    @Override
    public void kick(String playerName, String reason, String staffName) {
        dispatch("essentials:kick " + playerName + " " + reason);
    }

    private void dispatch(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
