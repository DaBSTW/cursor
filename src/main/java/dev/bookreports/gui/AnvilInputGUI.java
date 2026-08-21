package dev.bookreports.gui;

import dev.bookreports.config.LocaleManager;
import dev.bookreports.util.TextSanitizer;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * A minimal, dependency-free anvil-based text prompt: the player renames a paper item and the renamed name becomes the
 * "input". Written books cannot take free text (SPECS.md §4.2), so evidence collection is delegated here instead of to
 * another book page.
 *
 * <p>
 * Register once with {@code Bukkit.getPluginManager().registerEvents(gui, plugin)}.
 */
public final class AnvilInputGUI implements Listener {

    public static final int MAX_LENGTH = TextSanitizer.EVIDENCE_MAX_LENGTH;

    private final LocaleManager locale;
    private final Map<UUID, Inventory> openByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Consumer<Optional<String>>> callbacks = new ConcurrentHashMap<>();
    private final Map<UUID, String> submitted = new ConcurrentHashMap<>();

    public AnvilInputGUI(LocaleManager locale) {
        this.locale = Objects.requireNonNull(locale, "locale");
    }

    // Zeroing the repair cost keeps evidence submission free regardless of the reporter's XP level. The
    // replacement (AnvilView#setRepairCost) is tied to a player's already-open view, so it can't be used
    // here where the inventory is only just being constructed — AnvilInventory#setRepairCost is deprecated
    // for removal but still present as of Paper 26.2, and this is intentional until that changes.
    @SuppressWarnings("removal")
    public void open(Player player, Consumer<Optional<String>> onComplete) {
        Inventory inventory = Bukkit.createInventory(null, InventoryType.ANVIL, locale.get("evidence.prompt"));
        ItemStack prompt = ItemStack.of(Material.PAPER);
        ItemMeta meta = prompt.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(""));
            prompt.setItemMeta(meta);
        }
        inventory.setItem(0, prompt);
        if (inventory instanceof AnvilInventory anvil) {
            anvil.setRepairCost(0);
        }

        openByPlayer.put(player.getUniqueId(), inventory);
        callbacks.put(player.getUniqueId(), onComplete);
        player.openInventory(inventory);
    }

    @EventHandler
    @SuppressWarnings("removal")
    public void onPrepare(PrepareAnvilEvent event) {
        if (!openByPlayer.containsValue(event.getInventory())) {
            return;
        }
        if (event.getInventory() instanceof AnvilInventory anvil) {
            anvil.setRepairCost(0);
        }
        ItemStack input = event.getInventory().getItem(0);
        event.setResult(input == null ? null : input.clone());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory tracked = openByPlayer.get(player.getUniqueId());
        if (tracked == null || !tracked.equals(event.getInventory()) || event.getSlot() != 2) {
            return;
        }
        event.setCancelled(true);

        ItemStack result = event.getCurrentItem();
        ItemMeta resultMeta = result != null ? result.getItemMeta() : null;
        // The anvil rename field is a legacy plain-text string client-side; there is no Component form to read.
        @SuppressWarnings("deprecation")
        String rawName = resultMeta != null && resultMeta.hasDisplayName() ? resultMeta.getDisplayName() : null;
        submitted.put(player.getUniqueId(), sanitize(rawName));
        player.closeInventory();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory tracked = openByPlayer.remove(player.getUniqueId());
        if (tracked == null || !tracked.equals(event.getInventory())) {
            return;
        }
        Consumer<Optional<String>> callback = callbacks.remove(player.getUniqueId());
        String value = submitted.remove(player.getUniqueId());
        if (callback != null) {
            callback.accept(value == null || value.isBlank() ? Optional.empty() : Optional.of(value));
        }
    }

    /**
     * Re-checked server-side by {@code ReportService} too: the anvil GUI's client-side rename field has no length limit
     * of its own, so a malicious client could send arbitrarily long or color-coded text regardless of what the vanilla
     * UI shows.
     */
    static String sanitize(String raw) {
        return TextSanitizer.stripAndTruncate(raw, MAX_LENGTH);
    }
}
