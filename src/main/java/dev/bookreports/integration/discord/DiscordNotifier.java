package dev.bookreports.integration.discord;

import dev.bookreports.api.event.ReportCreatedEvent;
import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.DiscordSettings;
import dev.bookreports.storage.model.Report;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Discord webhook alerts (SPECS.md §11): a plain HTTP POST, no external library. One attempt per report, bounded by
 * {@link #TIMEOUT} — never retried, so a dead webhook can't pile up requests.
 */
public final class DiscordNotifier implements Listener {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final Supplier<BookReportsConfig> config;
    private final HttpClient httpClient;
    private final Logger logger;

    public DiscordNotifier(Supplier<BookReportsConfig> config, Executor executor, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).executor(executor).build();
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler
    public void onReportCreated(ReportCreatedEvent event) {
        DiscordSettings discord = config.get().discord();
        Report report = event.report();
        if (!discord.enabled() || report.priority().ordinal() < discord.minPriorityToNotify().ordinal()) {
            return;
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(discord.webhookUrl())).timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload(report))).build();
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Invalid discord.webhook-url in config.yml", e);
            return;
        }
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
            if (error != null) {
                logger.log(Level.WARNING, "Discord webhook notification failed", error);
            } else if (response.statusCode() >= 300) {
                logger.warning("Discord webhook returned HTTP " + response.statusCode());
            }
        });
    }

    private String payload(Report report) {
        String content = "⚠ New " + report.priority() + " report: **" + report.targetName() + "** — "
                + report.categoryId();
        return "{\"content\":\"" + escape(content) + "\"}";
    }

    private String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
