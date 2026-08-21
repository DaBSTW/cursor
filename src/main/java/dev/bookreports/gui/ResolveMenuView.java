package dev.bookreports.gui;

import dev.bookreports.config.LocaleManager;
import dev.bookreports.storage.model.ReportStatus;
import java.util.Objects;
import java.util.function.BiConsumer;
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
 * Quick rejection reasons (SPECS.md §5.3) — preconfigured resolutions, no free text needed.
 *
 * <p>
 * {@code resolutionNote} is stored as a stable, English reason code rather than pre-rendered text, so it stays
 * re-localizable and matches how {@code categoryId}/{@code subReasonId} are stored elsewhere.
 */
public final class ResolveMenuView implements InventoryHolder, Listener {

    private static final int SIZE = 9;
    private static final int NO_EVIDENCE_SLOT = 2;
    private static final int NOT_INFRACTION_SLOT = 4;
    private static final int DUPLICATE_SLOT = 6;

    private final Plugin plugin;
    private final Player viewer;
    private final LocaleManager locale;
    private final BiConsumer<ReportStatus, String> onChosen;
    private Inventory inventory;

    public ResolveMenuView(Plugin plugin, Player viewer, LocaleManager locale,
            BiConsumer<ReportStatus, String> onChosen) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.onChosen = Objects.requireNonNull(onChosen, "onChosen");
    }

    public void open() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        inventory = Bukkit.createInventory(this, SIZE, locale.get("staff.resolve-menu.title"));
        inventory.setItem(NO_EVIDENCE_SLOT, item(Material.PAPER, locale.get("staff.resolve-menu.no-evidence")));
        inventory.setItem(NOT_INFRACTION_SLOT, item(Material.SHIELD, locale.get("staff.resolve-menu.not-infraction")));
        inventory.setItem(DUPLICATE_SLOT, item(Material.BOOK, locale.get("staff.resolve-menu.duplicate")));
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
            case NO_EVIDENCE_SLOT -> choose(ReportStatus.RESOLVED_REJECTED, "NO_EVIDENCE");
            case NOT_INFRACTION_SLOT -> choose(ReportStatus.RESOLVED_REJECTED, "NOT_INFRACTION");
            case DUPLICATE_SLOT -> choose(ReportStatus.RESOLVED_DUPLICATE, "DUPLICATE");
            default -> {
                // Not one of our buttons — ignore.
            }
        }
    }

    private void choose(ReportStatus status, String reasonCode) {
        viewer.closeInventory();
        onChosen.accept(status, reasonCode);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
