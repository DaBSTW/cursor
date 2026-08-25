package dev.bookreports.api;

import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.service.ReportService;
import dev.bookreports.service.SubmitReportRequest;
import dev.bookreports.storage.model.Report;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.bukkit.Bukkit;

/** Bridges the public {@link BookReportsAPI} contract to the internal, Bukkit-light {@link ReportService}. */
public final class BookReportsApiImpl implements BookReportsAPI {

    private final ReportService reportService;
    private final Supplier<BookReportsConfig> config;
    private final Executor executor;

    public BookReportsApiImpl(ReportService reportService, Supplier<BookReportsConfig> config, Executor executor) {
        this.reportService = Objects.requireNonNull(reportService, "reportService");
        this.config = Objects.requireNonNull(config, "config");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public CompletableFuture<Report> submitReport(UUID reporter, UUID target, String categoryId, String subReasonId,
            String evidence) {
        return CompletableFuture
                .supplyAsync(() -> new SubmitReportRequest(reporter, resolveName(reporter), target, resolveName(target),
                        categoryId, subReasonId, evidence, config.get().serverId(), null, null, null), executor)
                .thenCompose(reportService::submitReport);
    }

    @Override
    public CompletableFuture<List<Report>> getReportHistory(UUID target) {
        return reportService.getReportHistory(target);
    }

    @Override
    public CompletableFuture<Optional<Report>> getReport(UUID reportUuid) {
        return reportService.getReport(reportUuid);
    }

    @Override
    public boolean isOnCooldown(UUID reporter) {
        return reportService.isOnCooldown(reporter);
    }

    private String resolveName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : "Unknown";
    }
}
