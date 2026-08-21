package dev.bookreports.gui;

import dev.bookreports.config.LocaleManager;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * Quick sanction actions (SPECS.md §11), only offered when a
 * {@link dev.bookreports.integration.punishment.PunishmentBridge} is active.
 */
public final class SanctionMenuView implements InventoryHolder, Listener {

    /** Which punishment the staff member picked. */
    public enum Action {
        KICK, MUTE, BAN
    }

    private static final int SIZE = 9;
    private static final int KICK_SLOT = 2;
    private static final int MUTE_SLOT = 4;
    private static final int BAN_SLOT = 6;

    private final Plugin plugin;
    private final Player viewer;
    private final LocaleManager locale;
    private final Consumer<Action> onChosen;
    private Inventory inventory;

    public SanctionMenuView(Plugin plugin, Player viewer, LocaleManager locale, Consumer<Action> onChosen) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.onChosen = Objects.requireNonNull(onChosen, "onChosen");
    }

    public void open() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        inventory = Bukkit.createInventory(this, SIZE, locale.get("staff.sanction-menu.title"));
        inventory.setItem(KICK_SLOT, item(Material.LEATHER_BOOTS, locale.get("staff.sanction-menu.kick")));
        inventory.setItem(MUTE_SLOT, item(Material.MUSIC_DISC_11, locale.get("staff.sanction-menu.mute")));
        inventory.setItem(BAN_SLOT, item(Material.BARRIER, locale.get("staff.sanction-menu.ban")));
        viewer.openInventory(inventory);
    }

    private ItemStack item(Material material, Component label) {
        ItemStack item = ItemStack.of(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(label);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this
                || !event.getWhoClicked().getUniqueId().equals(viewer.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        switch (event.getRawSlot()) {
            case KICK_SLOT -> choose(Action.KICK);
            case MUTE_SLOT -> choose(Action.MUTE);
            case BAN_SLOT -> choose(Action.BAN);
            default -> {
                // Not one of our buttons — ignore.
            }
        }
    }

    private void choose(Action action) {
        viewer.closeInventory();
        onChosen.accept(action);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
