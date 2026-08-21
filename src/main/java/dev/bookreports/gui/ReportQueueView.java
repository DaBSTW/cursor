package dev.bookreports.gui;

import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.LocaleManager;
import dev.bookreports.storage.dao.ReportDao;
import dev.bookreports.storage.model.Priority;
import dev.bookreports.storage.model.Report;
import dev.bookreports.storage.model.ReportStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

/** The staff review queue (SPECS.md §5.2): player heads, prioritized and filterable by status and category. */
public final class ReportQueueView extends PaginatedView {

    private static final int STATUS_FILTER_SLOT = 47;
    private static final int CATEGORY_FILTER_SLOT = 51;
    private static final ReportStatus[] STATUS_FILTERS = {ReportStatus.PENDING, ReportStatus.IN_REVIEW,
            ReportStatus.RESOLVED_ACTION, ReportStatus.RESOLVED_REJECTED};

    private final ReportDao reportDao;
    private final Supplier<BookReportsConfig> config;
    private final LocaleManager locale;
    private final Executor executor;
    private final Consumer<Report> onSelect;
    private int statusFilterIndex;
    private String categoryFilter;
    private List<Report> currentPageReports = List.of();

    public ReportQueueView(Plugin plugin, Player viewer, ReportDao reportDao, Supplier<BookReportsConfig> config,
            LocaleManager locale, Executor executor, Consumer<Report> onSelect) {
        super(plugin, viewer);
        this.reportDao = Objects.requireNonNull(reportDao, "reportDao");
        this.config = Objects.requireNonNull(config, "config");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
    }

    private ReportStatus statusFilter() {
        return STATUS_FILTERS[statusFilterIndex];
    }

    @Override
    protected Component title() {
        return locale.get("staff.queue.title");
    }

    /**
     * Both the DB read and the {@code groupingBadge} lookups it triggers happen here, off the main thread; the fetched
     * reports are cached in {@link #currentPageReports} for {@link #onContentClick} to reuse without a second round
     * trip once this future's items are on screen.
     */
    @Override
    protected CompletableFuture<List<ItemStack>> contentItemsAsync(int page) {
        return CompletableFuture.supplyAsync(() -> {
            List<Report> reports = reportDao.findByStatus(statusFilter(), categoryFilter, page, CONTENT_SLOTS);
            currentPageReports = reports;
            List<ItemStack> items = new ArrayList<>();
            for (Report report : reports) {
                items.add(toItem(report));
            }
            return items;
        }, executor);
    }

    @Override
    protected void onContentClick(int slotInPage, int page) {
        if (slotInPage < currentPageReports.size()) {
            onSelect.accept(currentPageReports.get(slotInPage));
        }
    }

    @Override
    protected Map<Integer, ItemStack> extraBorderItems() {
        return Map.of(STATUS_FILTER_SLOT, statusFilterItem(), CATEGORY_FILTER_SLOT, categoryFilterItem());
    }

    private ItemStack statusFilterItem() {
        ItemStack item = ItemStack.of(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(locale.get("staff.queue.filter", Map.of("status", statusFilter().name())));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack categoryFilterItem() {
        ItemStack item = ItemStack.of(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String label = categoryFilter != null ? categoryFilter : "ALL";
            meta.displayName(locale.get("staff.queue.filter", Map.of("status", label)));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    protected void onExtraBorderClick(int slot) {
        if (slot == STATUS_FILTER_SLOT) {
            statusFilterIndex = (statusFilterIndex + 1) % STATUS_FILTERS.length;
            open(0);
        } else if (slot == CATEGORY_FILTER_SLOT) {
            categoryFilter = nextCategoryFilter();
            open(0);
        }
    }

    private String nextCategoryFilter() {
        List<String> categoryIds = new ArrayList<>(config.get().categories().keySet());
        if (categoryIds.isEmpty()) {
            return null;
        }
        if (categoryFilter == null) {
            return categoryIds.get(0);
        }
        int next = categoryIds.indexOf(categoryFilter) + 1;
        return next < categoryIds.size() ? categoryIds.get(next) : null;
    }

    private ItemStack toItem(Report report) {
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(report.targetUuid()));
            meta.displayName(Component.text(report.targetName()));
            List<Component> lore = new ArrayList<>(
                    List.of(locale.get("staff.queue.lore.category", Map.of("category", report.categoryId())),
                            locale.get("staff.queue.lore.priority", Map.of("priority", report.priority().name())),
                            locale.get("staff.queue.lore.reporter", Map.of("player", report.reporterName()))));
            if (report.priority() == Priority.HIGH) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                groupingBadge(report).ifPresent(lore::add);
            }
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    /** "3 reports in the last 10 min" — the consensus that likely triggered the escalation to HIGH. */
    private Optional<Component> groupingBadge(Report report) {
        var escalation = config.get().priorityEscalation();
        Instant windowStart = Instant.now().minusSeconds(escalation.windowSeconds());
        long distinctReporters = reportDao.findByTarget(report.targetUuid()).stream()
                .filter(r -> r.categoryId().equals(report.categoryId()))
                .filter(r -> !r.createdAt().isBefore(windowStart)).map(Report::reporterUuid).distinct().count();
        if (distinctReporters < escalation.distinctReportersThreshold()) {
            return Optional.empty();
        }
        return Optional
                .of(locale.get("staff.queue.lore.reporters-count", Map.of("count", String.valueOf(distinctReporters))));
    }
}
