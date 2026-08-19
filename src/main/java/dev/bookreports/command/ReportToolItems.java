package dev.bookreports.command;

import dev.bookreports.config.LocaleManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** The optional physical report-tool item (SPECS.md §4.6), tagged via PDC so renamed lookalikes never match. */
final class ReportToolItems {

    static final NamespacedKey KEY = new NamespacedKey("bookreports", "report_tool");

    private ReportToolItems() {
    }

    static ItemStack create(LocaleManager locale) {
        ItemStack tool = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = tool.getItemMeta();
        if (meta != null) {
            meta.displayName(locale.get("report.tool-name"));
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
            tool.setItemMeta(meta);
        }
        return tool;
    }

    static boolean isReportTool(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }
}
