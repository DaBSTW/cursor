package dev.pregen.listener;

import dev.pregen.command.ConfirmationService;
import dev.pregen.generation.GenerationService;
import dev.pregen.progress.BossBarDisplay;
import dev.pregen.selection.SelectionRegistry;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/** Drops per-player state on quit and releases tasks of worlds being unloaded, so nothing outlives its owner. */
public final class SessionListener implements Listener {

    private final SelectionRegistry selections;
    private final ConfirmationService confirmations;
    private final BossBarDisplay bossBars;
    private final GenerationService generation;

    public SessionListener(SelectionRegistry selections, ConfirmationService confirmations, BossBarDisplay bossBars,
            GenerationService generation) {
        this.selections = selections;
        this.confirmations = confirmations;
        this.bossBars = bossBars;
        this.generation = generation;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        selections.forget(id);
        confirmations.forget(id);
        bossBars.forget(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        generation.evict(event.getWorld().getName());
    }
}
