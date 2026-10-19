package dev.example.soledom.core.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.runtime.ModelManager;

/** Rebuilds models when their chunk loads and releases them when it unloads. */
public final class EntityLifecycleListener implements Listener {

    private final ModelManager manager;

    public EntityLifecycleListener(@NotNull ModelManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        manager.handleEntitiesLoaded(event.getEntities());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        manager.handleEntitiesUnloaded(event.getEntities());
    }
}
