package dev.example.soledom.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.api.ModelController;

/** Fired when a player left- or right-clicks a model hitbox. Cancelling it cancels the vanilla interaction. */
public final class SoledomInteractEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ModelController model;
    private final Action action;
    private final EquipmentSlot hand;
    private boolean cancelled;

    public SoledomInteractEvent(@NotNull Player player, @NotNull ModelController model, @NotNull Action action,
            @NotNull EquipmentSlot hand) {
        this.player = player;
        this.model = model;
        this.action = action;
        this.hand = hand;
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull ModelController getModel() {
        return model;
    }

    public @NotNull Action getAction() {
        return action;
    }

    public @NotNull EquipmentSlot getHand() {
        return hand;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

    public enum Action {
        LEFT_CLICK,
        RIGHT_CLICK
    }
}
