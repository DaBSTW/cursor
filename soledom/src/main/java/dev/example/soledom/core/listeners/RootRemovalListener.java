package dev.example.soledom.core.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.runtime.ModelKeys;
import dev.example.soledom.runtime.ModelManager;

/**
 * Cleans up parts when a root entity is removed by something other than a chunk unload ({@code /kill}, another
 * plugin). Registered separately so a server without {@link EntityRemoveEvent} just skips this feature.
 */
public final class RootRemovalListener implements Listener {

    private final ModelManager manager;
    private final ModelKeys keys;

    public RootRemovalListener(@NotNull ModelManager manager, @NotNull ModelKeys keys) {
        this.manager = manager;
        this.keys = keys;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent event) {
        EntityRemoveEvent.Cause cause = event.getCause();
        if (cause == EntityRemoveEvent.Cause.UNLOAD || cause == EntityRemoveEvent.Cause.PLAYER_QUIT) {
            return;
        }
        if (keys.isRoot(event.getEntity())) {
            manager.handleRootRemoved(event.getEntity());
        }
    }
}
