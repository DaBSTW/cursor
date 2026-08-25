package dev.bookreports.gui;

import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.LocaleManager;
import dev.bookreports.integration.punishment.PunishmentBridge;
import dev.bookreports.integration.vault.VaultBridge;
import dev.bookreports.service.ReportService;
import dev.bookreports.storage.model.Report;
import dev.bookreports.storage.model.ReportStatus;
import dev.bookreports.storage.model.ReporterStats;
import dev.bookreports.util.LocationCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
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
    private static final int INCIDENT_TELEPORT_SLOT = 13;
    private static final int RESOLVE_SANCTION_SLOT = 14;
    private static final int RESOLVE_REJECT_SLOT = 15;
    private static final int MARK_FALSE_SLOT = 16;
    private static final int NOTES_SLOT = 19;
    private static final int BACK_SLOT = 22;

    private final Plugin plugin;
    private final Player viewer;
    private final LocaleManager locale;
    private final ReportService reportService;
    private final Optional<PunishmentBridge> punishmentBridge;
    private final Optional<VaultBridge> vaultBridge;
    private final Supplier<BookReportsConfig> config;
    private final Runnable onBack;
    private Report report;
    private ReporterStats reporterStats;
    private Inventory inventory;
    private boolean registered;

    public ReportDetailView(Plugin plugin, Player viewer, LocaleManager locale, ReportService reportService,
            Optional<PunishmentBridge> punishmentBridge, Supplier<BookReportsConfig> config, Report report,
            Runnable onBack, Optional<VaultBridge> vaultBridge) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.reportService = Objects.requireNonNull(reportService, "reportService");
        this.punishmentBridge = Objects.requireNonNull(punishmentBridge, "punishmentBridge");
        this.config = Objects.requireNonNull(config, "config");
        this.report = Objects.requireNonNull(report, "report");
        this.onBack = Objects.requireNonNull(onBack, "onBack");
        this.vaultBridge = Objects.requireNonNull(vaultBridge, "vaultBridge");
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
        inventory.setItem(INCIDENT_TELEPORT_SLOT,
                button(Material.COMPASS, locale.get("staff.detail.teleport-incident")));
        inventory.setItem(RESOLVE_SANCTION_SLOT,
                button(Material.IRON_SWORD, locale.get("staff.detail.resolve-sanction")));
        inventory.setItem(RESOLVE_REJECT_SLOT, button(Material.REDSTONE, locale.get("staff.detail.resolve-reject")));
        inventory.setItem(MARK_FALSE_SLOT, button(Material.BARRIER, locale.get("staff.detail.mark-false")));
        inventory.setItem(NOTES_SLOT, button(Material.WRITABLE_BOOK, locale.get("staff.detail.notes")));
        inventory.setItem(BACK_SLOT, button(Material.ARROW, locale.get("staff.detail.back")));
    }

    private ItemStack infoItem() {
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(report.targetUuid()));
            meta.displayName(decoratedName(report.targetUuid(), report.targetName()));
            String reviewer = report.reviewerUuid() != null
                    ? String.valueOf(Bukkit.getOfflinePlayer(report.reviewerUuid()).getName())
                    : null;
            List<Component> lore = new ArrayList<>(
                    List.of(locale.get("staff.detail.target", Map.of("player", report.targetName())),
                            locale.get("staff.detail.reporter", Map.of("player", report.reporterName())),
                            locale.get("staff.detail.category", Map.of("category", report.categoryId())),
                            locale.get("staff.detail.status", Map.of("status", report.status().name()))));
            lore.addAll(liveStatusLore());
            lore.add(locale.get("staff.detail.evidence",
                    Map.of("evidence", report.evidenceText() != null ? report.evidenceText() : "-")));
            if (reporterStats != null && reporterStats.total() > 0) {
                lore.add(locale.get("staff.detail.reporter-accuracy",
                        Map.of("accuracy", String.valueOf(reporterStats.accuracyPercent()), "total",
                                String.valueOf(reporterStats.total()))));
            }
            distanceAtSubmission()
                    .ifPresent(distance -> lore.add(locale.get("staff.detail.distance", Map.of("distance", distance))));
            if (report.chatContext() != null && !report.chatContext().isBlank()) {
                lore.add(locale.get("staff.detail.chat-context", Map.of("context", report.chatContext())));
            }
            if (report.coreProtectContext() != null && !report.coreProtectContext().isBlank()) {
                lore.add(locale.get("staff.detail.coreprotect-context",
                        Map.of("activity", report.coreProtectContext())));
            }
            if (report.sanctionType() != null) {
                String sanctionLabel = report.sanctionDuration() != null
                        ? report.sanctionType() + " (" + report.sanctionDuration() + ")"
                        : report.sanctionType();
                lore.add(locale.get("staff.detail.sanction", Map.of("sanction", sanctionLabel)));
            }
            lore.add(reviewer != null
                    ? locale.get("staff.detail.claimed-by", Map.of("player", reviewer))
                    : locale.get("staff.detail.unclaimed"));
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * How far apart the reporter and target actually were when the report was filed — a cheap, useful credibility
     * signal TigerReports doesn't surface at all: a reporter claiming to have witnessed something from 300 blocks away,
     * in a different world, is worth a second look. Empty whenever either snapshot is missing/undecodable or the two
     * are in different worlds (a cross-world distance is meaningless).
     */
    private Optional<String> distanceAtSubmission() {
        Optional<Location> targetLoc = LocationCodec.decode(report.targetLocation());
        Optional<Location> reporterLoc = LocationCodec.decode(report.reporterLocation());
        if (targetLoc.isEmpty() || reporterLoc.isEmpty()
                || !Objects.equals(targetLoc.get().getWorld(), reporterLoc.get().getWorld())) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf((int) targetLoc.get().distance(reporterLoc.get())));
    }

    /**
     * Deliberately computed live rather than snapshotted at report-creation time (unlike {@code chatContext}/
     * {@code coreProtectContext}): gamemode/health/effects are meant to answer "what is this player doing *right now*",
     * which a stored value would get stale the moment the target moves — and it costs nothing extra since the target's
     * {@link Player} object, if online, is already resident in memory (no DB round trip, no allocation beyond a couple
     * of short-lived strings).
     */
    private List<Component> liveStatusLore() {
        Player target = Bukkit.getPlayer(report.targetUuid());
        if (target == null) {
            return List.of(locale.get("staff.detail.live-status-offline"));
        }
        List<Component> lines = new ArrayList<>(2);
        lines.add(locale.get("staff.detail.live-status",
                Map.of("gamemode", target.getGameMode().name(), "health",
                        String.valueOf((int) Math.ceil(target.getHealth())), "max_health",
                        String.valueOf((int) target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()))));
        String effects = effectsSummary(target);
        if (!effects.isEmpty()) {
            lines.add(locale.get("staff.detail.live-status-effects", Map.of("effects", effects)));
        }
        return lines;
    }

    /**
     * Capped at 5 effects — a heavily-stacked player (creative testing, a vanilla beacon field) shouldn't blow up the
     * tooltip; staff only need a quick read, not a full status-effect audit.
     */
    private String effectsSummary(Player target) {
        return target.getActivePotionEffects().stream().limit(5)
                .map(effect -> effect.getType().getName() + " " + toRoman(effect.getAmplifier() + 1))
                .collect(Collectors.joining(", "));
    }

    private static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    /**
     * {@code name} with the player's Vault rank prefix/suffix around it, when a Vault {@code Chat} provider is active —
     * purely cosmetic, so a lookup failure or a missing bridge just falls back to the plain name. Legacy
     * ({@code §}-coded) prefix/suffix strings are the norm across permission plugins, hence the legacy deserializer
     * rather than MiniMessage here.
     */
    private Component decoratedName(UUID playerUuid, String name) {
        if (vaultBridge.isEmpty()) {
            return Component.text(name);
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        String prefix = vaultBridge.get().prefix(player);
        String suffix = vaultBridge.get().suffix(player);
        if (prefix.isEmpty() && suffix.isEmpty()) {
            return Component.text(name);
        }
        return LegacyComponentSerializer.legacySection().deserialize(prefix + name + suffix);
    }

    private ItemStack button(Material material, Component label) {
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
            case CLAIM_SLOT -> claim();
            case TELEPORT_SLOT -> teleport();
            case HISTORY_SLOT -> viewHistory();
            case INCIDENT_TELEPORT_SLOT -> teleportToIncidentLocation();
            case NOTES_SLOT -> viewNotes();
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

    /**
     * Teleports to where the target was standing when the report was filed — the actual scene of the alleged incident —
     * as opposed to {@link #teleport()}, which goes to wherever the target happens to be *right now*. Both are useful
     * and deliberately kept as separate buttons: a target who has long since moved on makes the live teleport useless
     * for reviewing block damage, chat location, etc.
     */
    private void teleportToIncidentLocation() {
        LocationCodec.decode(report.targetLocation()).ifPresentOrElse(viewer::teleport,
                () -> viewer.sendMessage(locale.get("staff.detail.no-location")));
    }

    /**
     * Read-only — adding a note is deliberately kept to {@code /reportadmin note <id> <text>} rather than a second
     * anvil-input layer here, since that already exists, is quick to type, and works from the console too.
     */
    private void viewNotes() {
        reportService.getNotes(report.id()).whenComplete((notes, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                viewer.sendMessage(locale.get("error.generic"));
                return;
            }
            if (notes.isEmpty()) {
                viewer.sendMessage(locale.get("staff.note.empty"));
                return;
            }
            viewer.sendMessage(locale.get("staff.note.title", Map.of("ticket_id", String.valueOf(report.id()))));
            for (var note : notes) {
                viewer.sendMessage(Component.text(note.authorName() + ": " + note.noteText()));
            }
        }));
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
        String duration = null;
        switch (action) {
            case KICK -> bridge.kick(targetName, reasonCode, staffName);
            case MUTE -> {
                duration = config.get().punishments().defaultMuteDuration();
                bridge.mute(targetName, duration, reasonCode, staffName);
            }
            case BAN -> {
                duration = config.get().punishments().defaultBanDuration();
                bridge.ban(targetName, duration, reasonCode, staffName);
            }
        }
        // Recorded separately from — and independent of — the resolve() call below: a failure here is a lost
        // audit-trail detail, not a reason to block the resolution the staff member just performed.
        reportService.recordSanction(report.id(), action.name(), duration).exceptionally(error -> {
            plugin.getLogger().log(Level.WARNING, "Failed to record sanction audit for report id=" + report.id(),
                    error);
            return null;
        });
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
