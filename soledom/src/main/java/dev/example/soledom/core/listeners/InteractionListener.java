package dev.example.soledom.core.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import dev.example.soledom.api.events.SoledomInteractEvent;
import dev.example.soledom.runtime.ModelEntityController;
import dev.example.soledom.runtime.ModelManager;

/** Translates clicks on model hitboxes ({@link Interaction} roots) into {@link SoledomInteractEvent}s. */
public final class InteractionListener implements Listener {

    private final ModelManager manager;

    public InteractionListener(@NotNull ModelManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEntityEvent event) {
        // The client sends one packet per hand; only react to the main hand so each click fires once.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (fire(event.getPlayer(), event.getRightClicked(), SoledomInteractEvent.Action.RIGHT_CLICK, event.getHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeftClick(PrePlayerAttackEntityEvent event) {
        if (fire(event.getPlayer(), event.getAttacked(), SoledomInteractEvent.Action.LEFT_CLICK, EquipmentSlot.HAND)) {
            event.setCancelled(true);
        }
    }

    /** @return true when the vanilla event should be cancelled */
    private boolean fire(Player player, Entity clicked, SoledomInteractEvent.Action action, EquipmentSlot hand) {
        if (!(clicked instanceof Interaction)) {
            return false;
        }
        ModelEntityController controller = manager.find(clicked).orElse(null);
        if (controller == null) {
            return false;
        }
        SoledomInteractEvent event = new SoledomInteractEvent(player, controller, action, hand);
        // Attacks on model hitboxes are always swallowed: an Interaction has nothing to damage.
        return !event.callEvent() || action == SoledomInteractEvent.Action.LEFT_CLICK;
    }
}
