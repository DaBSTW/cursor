package dev.bookreports.integration.punishment;

import org.bukkit.Bukkit;

/** Dispatches AdvancedBan's console commands, prefixed with its plugin namespace to dodge alias collisions. */
final class AdvancedBanBridge implements PunishmentBridge {

    @Override
    public String name() {
        return "AdvancedBan";
    }

    @Override
    public void ban(String playerName, String duration, String reason, String staffName) {
        dispatch("advancedban:ban " + playerName + " " + duration + " " + reason);
    }

    @Override
    public void mute(String playerName, String duration, String reason, String staffName) {
        dispatch("advancedban:mute " + playerName + " " + duration + " " + reason);
    }

    @Override
    public void kick(String playerName, String reason, String staffName) {
        dispatch("advancedban:kick " + playerName + " " + reason);
    }

    private void dispatch(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
