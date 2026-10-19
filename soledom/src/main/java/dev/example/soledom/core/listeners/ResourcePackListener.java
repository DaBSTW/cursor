package dev.example.soledom.core.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.resourcepack.PackService;

/** Sends the configured resource pack on join ({@code resource-pack.send-on-join}). */
public final class ResourcePackListener implements Listener {

    private final PackService packs;

    public ResourcePackListener(@NotNull PackService packs) {
        this.packs = packs;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        packs.sendOnJoin(event.getPlayer());
    }
}
