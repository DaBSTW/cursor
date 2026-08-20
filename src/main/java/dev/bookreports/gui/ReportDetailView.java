package dev.bookreports.gui;

import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.LocaleManager;
import dev.bookreports.integration.punishment.PunishmentBridge;
import dev.bookreports.service.ReportService;
import dev.bookreports.storage.model.Report;
import dev.bookreports.storage.model.ReportStatus;
import dev.bookreports.storage.model.ReporterStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

/** One report's full detail and staff actions (SPECS.md §5.3). */
public final class ReportDetailView implements InventoryHolder, Listener {

    private static final int SIZE = 27;
    private static final int INFO_SLOT = 4;
    private static final int CLAIM_SLOT = 10;
    private static final int TELEPORT_SLOT = 11;
    private static final int HISTORY_SLOT = 12;
    private static final int RESOLVE_SANCTION_SLOT = 14;
    private static final int RESOLVE_REJECT_SLOT = 15;
    private static final int MARK_FALSE_SLOT = 16;
    private static final int BACK_SLOT = 22;

    private final Plugin plugin;
    private final Player viewer;
    private final LocaleManager locale;
    private final ReportService reportService;
    private final Optional<PunishmentBridge> punishmentBridge;
    private final Supplier<BookReportsConfig> config;
    private final Runnable onBack;
    private Report report;
    private ReporterStats reporterStats;
    private Inventory inventory;
    private boolean registered;

    public ReportDetailView(Plugin plugin, Player viewer, LocaleManager locale, ReportService reportService,
            Optional<PunishmentBridge> punishmentBridge, Supplier<BookReportsConfig> config, Report report,
            Runnable onBack) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.reportService = Objects.requireNonNull(reportService, "reportService");
        this.punishmentBridge = Objects.requireNonNull(punishmentBridge, "punishmentBridge");
        this.config = Objects.requireNonNull(config, "config");
        this.report = Objects.requireNonNull(report, "report");
        this.onBack = Objects.requireNonNull(onBack, "onBack");
    }

    /** Loads the reporter's track record before the first render — {@link #refresh} reuses it, it doesn't change. */
    public void open() {
        reportService.reporterStats(report.reporterUuid())
                .whenComplete((stats, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    this.reporterStats = error == null ? stats : null;
                    if (!registered) {
                        Bukkit.getPluginManager().registerEvents(this, plugin);
                        registered = true;
                    }
                    inventory = Bukkit.createInventory(this, SIZE,
                            locale.get("staff.detail.title", Map.of("ticket_id", String.valueOf(report.id()))));
                    render();
                    viewer.openInventory(inventory);
                }));
    }

    private void render() {
        inventory.clear();
        inventory.setItem(INFO_SLOT, infoItem());
        inventory.setItem(CLAIM_SLOT, button(Material.LIME_DYE, locale.get("staff.detail.claim")));
        inventory.setItem(TELEPORT_SLOT, button(Material.ENDER_PEARL, locale.get("staff.detail.teleport")));
        inventory.setItem(HISTORY_SLOT, button(Material.BOOK, locale.get("staff.detail.history")));
        inventory.setItem(RESOLVE_SANCTION_SLOT,
                button(Material.IRON_SWORD, locale.get("staff.detail.resolve-sanction")));
        inventory.setItem(RESOLVE_REJECT_SLOT, button(Material.REDSTONE, locale.get("staff.detail.resolve-reject")));
        inventory.setItem(MARK_FALSE_SLOT, button(Material.BARRIER, locale.get("staff.detail.mark-false")));
        inventory.setItem(BACK_SLOT, button(Material.ARROW, locale.get("staff.detail.back")));
    }

    private ItemStack infoItem() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(report.targetUuid()));
            meta.displayName(Component.text(report.targetName()));
            String reviewer = report.reviewerUuid() != null
                    ? String.valueOf(Bukkit.getOfflinePlayer(report.reviewerUuid()).getName())
                    : null;
            List<Component> lore = new ArrayList<>(
                    List.of(locale.get("staff.detail.target", Map.of("player", report.targetName())),
                            locale.get("staff.detail.reporter", Map.of("player", report.reporterName())),
                            locale.get("staff.detail.category", Map.of("category", report.categoryId())),
                            locale.get("staff.detail.status", Map.of("status", report.status().name())),
                            locale.get("staff.detail.evidence",
                                    Map.of("evidence", report.evidenceText() != null ? report.evidenceText() : "-"))));
            if (reporterStats != null && reporterStats.total() > 0) {
                lore.add(locale.get("staff.detail.reporter-accuracy",
                        Map.of("accuracy", String.valueOf(reporterStats.accuracyPercent()), "total",
                                String.valueOf(reporterStats.total()))));
            }
            if (report.chatContext() != null && !report.chatContext().isBlank()) {
                lore.add(locale.get("staff.detail.chat-context", Map.of("context", report.chatContext())));
            }
            lore.add(reviewer != null
                    ? locale.get("staff.detail.claimed-by", Map.of("player", reviewer))
                    : locale.get("staff.detail.unclaimed"));
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack button(Material material, Component label) {
        ItemStack item = new ItemStack(material);
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
            case CLAIM_SLOT -> claim();
            case TELEPORT_SLOT -> teleport();
            case HISTORY_SLOT -> viewHistory();
            case RESOLVE_SANCTION_SLOT -> onSanctionRequested();
            case RESOLVE_REJECT_SLOT -> openResolveMenu();
            case MARK_FALSE_SLOT -> markFalse();
            case BACK_SLOT -> {
                viewer.closeInventory();
                onBack.run();
            }
            default -> {
                // Info item and empty slots have no action.
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() == this) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    private void claim() {
        reportService.claim(report.id(), viewer.getUniqueId())
                .whenComplete((claimed, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (Boolean.TRUE.equals(claimed)) {
                        viewer.sendMessage(
                                locale.get("staff.claim.success", Map.of("ticket_id", String.valueOf(report.id()))));
                        refresh();
                    } else {
                        viewer.sendMessage(locale.get("staff.claim.already-claimed", Map.of("reviewer", "?")));
                    }
                }));
    }

    private void teleport() {
        Player target = Bukkit.getPlayer(report.targetUuid());
        if (target == null) {
            viewer.sendMessage(locale.get("report.target-offline"));
            return;
        }
        viewer.teleport(target.getLocation());
    }

    private void viewHistory() {
        reportService.getReportHistory(report.targetUuid())
                .whenComplete((history, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        viewer.sendMessage(locale.get("error.generic"));
                        return;
                    }
                    viewer.sendMessage(locale.get("staff.history.title", Map.of("player", report.targetName())));
                    for (Report past : history) {
                        viewer.sendMessage(
                                Component.text("#" + past.id() + " " + past.categoryId() + " " + past.status()));
                    }
                }));
    }

    private void resolve(ReportStatus status, String reasonCode) {
        reportService.resolve(report.id(), status, viewer.getUniqueId(), reasonCode).whenComplete(
                (updated, error) -> Bukkit.getScheduler().runTask(plugin, () -> onResolved(updated, error)));
    }

    private void markFalse() {
        reportService.markFalse(report.id(), viewer.getUniqueId(), "REVIEWED_FALSE").whenComplete(
                (updated, error) -> Bukkit.getScheduler().runTask(plugin, () -> onResolved(updated, error)));
    }

    private void onResolved(Boolean updated, Throwable error) {
        if (error != null || !Boolean.TRUE.equals(updated)) {
            viewer.sendMessage(locale.get("staff.resolve.already-resolved"));
            return;
        }
        viewer.sendMessage(locale.get("staff.resolve.success",
                Map.of("ticket_id", String.valueOf(report.id()), "resolution", "OK")));
        viewer.closeInventory();
        onBack.run();
    }

    private void openResolveMenu() {
        new ResolveMenuView(plugin, viewer, locale, (status, reasonCode) -> resolve(status, reasonCode)).open();
    }

    /** With no bridge active, sanctions can only happen out-of-band — just record that fact. */
    private void onSanctionRequested() {
        if (punishmentBridge.isEmpty()) {
            resolve(ReportStatus.RESOLVED_ACTION, "EXTERNAL_ACTION_APPLIED");
            return;
        }
        new SanctionMenuView(plugin, viewer, locale, this::applySanction).open();
    }

    private void applySanction(SanctionMenuView.Action action) {
        PunishmentBridge bridge = punishmentBridge.orElseThrow();
        String targetName = report.targetName();
        String staffName = viewer.getName();
        String reasonCode = "SANCTION_" + report.categoryId();
        switch (action) {
            case KICK -> bridge.kick(targetName, reasonCode, staffName);
            case MUTE ->
                bridge.mute(targetName, config.get().punishments().defaultMuteDuration(), reasonCode, staffName);
            case BAN -> bridge.ban(targetName, config.get().punishments().defaultBanDuration(), reasonCode, staffName);
        }
        resolve(ReportStatus.RESOLVED_ACTION, "SANCTION_" + action.name());
    }

    private void refresh() {
        reportService.getReport(report.uuid())
                .thenAccept(updated -> updated.ifPresent(value -> Bukkit.getScheduler().runTask(plugin, () -> {
                    this.report = value;
                    render();
                })));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
