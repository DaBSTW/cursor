package dev.bookreports.integration.punishment;

import org.bukkit.Bukkit;

/** Dispatches LiteBans' console commands, prefixed with its plugin namespace to dodge alias collisions. */
final class LiteBansBridge implements PunishmentBridge {

    @Override
    public String name() {
        return "LiteBans";
    }

    @Override
    public void ban(String playerName, String duration, String reason, String staffName) {
        dispatch("litebans:ban " + playerName + " " + duration + " " + reason);
    }

    @Override
    public void mute(String playerName, String duration, String reason, String staffName) {
        dispatch("litebans:mute " + playerName + " " + duration + " " + reason);
    }

    @Override
    public void kick(String playerName, String reason, String staffName) {
        dispatch("litebans:kick " + playerName + " " + reason);
    }

    private void dispatch(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
