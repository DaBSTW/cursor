package dev.bookreports.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * A reusable 6-row chest GUI: rows 1-5 (slots 0-44) hold content, row 6 is a fixed border with previous/close/next. One
 * instance per open view — it registers itself as a listener on {@link #open} and unregisters on close, so it never
 * outlives the inventory it drew.
 */
public abstract class PaginatedView implements InventoryHolder, Listener {

    public static final int CONTENT_SLOTS = 45;
    private static final int SIZE = 54;
    private static final int PREV_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final Plugin plugin;
    private final Player viewer;
    private Inventory inventory;
    private int page;
    private boolean registered;

    protected PaginatedView(Plugin plugin, Player viewer) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
    }

    /** Loads this page's content off-thread first — {@link #contentItemsAsync} may hit the database. */
    public final void open(int requestedPage) {
        this.page = Math.max(0, requestedPage);
        contentItemsAsync(page).whenComplete((items, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Failed to load a page for " + viewer.getName(), error);
                return;
            }
            if (!registered) {
                Bukkit.getPluginManager().registerEvents(this, plugin);
                registered = true;
            }
            this.inventory = Bukkit.createInventory(this, SIZE, title());
            render(items);
            viewer.openInventory(inventory);
        }));
    }

    private void render(List<ItemStack> items) {
        inventory.clear();
        for (int i = 0; i < items.size() && i < CONTENT_SLOTS; i++) {
            inventory.setItem(i, items.get(i));
        }
        if (page > 0) {
            inventory.setItem(PREV_SLOT, borderItem(Material.ARROW, "«"));
        }
        inventory.setItem(CLOSE_SLOT, borderItem(Material.BARRIER, "✕"));
        if (items.size() >= CONTENT_SLOTS) {
            inventory.setItem(NEXT_SLOT, borderItem(Material.ARROW, "»"));
        }
        extraBorderItems().forEach(inventory::setItem);
    }

    private ItemStack borderItem(Material material, String label) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(label, NamedTextColor.GRAY));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public final void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this
                || !event.getWhoClicked().getUniqueId().equals(viewer.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) {
            return;
        }
        if (slot == PREV_SLOT && page > 0) {
            open(page - 1);
        } else if (slot == NEXT_SLOT) {
            open(page + 1);
        } else if (slot == CLOSE_SLOT) {
            viewer.closeInventory();
        } else if (slot < CONTENT_SLOTS) {
            onContentClick(slot, page);
        } else if (extraBorderItems().containsKey(slot)) {
            onExtraBorderClick(slot);
        }
    }

    @EventHandler
    public final void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() == this) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    @Override
    public final Inventory getInventory() {
        return inventory;
    }

    protected final Player viewer() {
        return viewer;
    }

    protected abstract Component title();

    /**
     * Items for this page's content slots (0..{@link #CONTENT_SLOTS}), most-important first. Runs off the main thread —
     * implementations that hit the database must do so inside the returned future, never inline.
     */
    protected abstract CompletableFuture<List<ItemStack>> contentItemsAsync(int page);

    protected abstract void onContentClick(int slotInPage, int page);

    /** Optional extra buttons in the border row (slots 45-53, avoid 45/49/53 — those are prev/close/next). */
    protected Map<Integer, ItemStack> extraBorderItems() {
        return Map.of();
    }

    protected void onExtraBorderClick(int slot) {
        // No-op by default.
    }
}
