package dev.bookreports.integration.discord;

import dev.bookreports.api.event.ReportCreatedEvent;
import dev.bookreports.api.event.ReportResolvedEvent;
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
 * Discord webhook alerts (SPECS.md §11): a plain HTTP POST, no external library, sent on report creation and again on
 * resolution. One attempt per notification, bounded by {@link #TIMEOUT} — never retried, so a dead webhook can't pile
 * up requests.
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
        Report report = event.report();
        if (!eligible(report)) {
            return;
        }
        String content = "⚠ New " + report.priority() + " report: **" + report.targetName() + "** — "
                + report.categoryId();
        send(content);
    }

    /**
     * Follows up only on reports that met the same threshold as {@link #onReportCreated} — those are the ones staff
     * were pinged about in the first place, so Discord shows their full lifecycle rather than every routine resolution.
     */
    @EventHandler
    public void onReportResolved(ReportResolvedEvent event) {
        Report report = event.report();
        if (!eligible(report)) {
            return;
        }
        String content = "✅ Report #" + report.id() + " against **" + report.targetName() + "** resolved: "
                + report.status();
        send(content);
    }

    private boolean eligible(Report report) {
        DiscordSettings discord = config.get().discord();
        return discord.enabled() && report.priority().ordinal() >= discord.minPriorityToNotify().ordinal();
    }

    private void send(String content) {
        DiscordSettings discord = config.get().discord();
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(discord.webhookUrl())).timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload(content))).build();
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

    private String payload(String content) {
        return "{\"content\":\"" + escape(content) + "\"}";
    }

    private String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
