package dev.bookreports.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.PriorityEscalationSettings;
import dev.bookreports.config.ReportCategory;
import dev.bookreports.storage.model.Priority;
import dev.bookreports.storage.model.Report;
import dev.bookreports.storage.model.ReportStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PriorityCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final ReportCategory hacks = new ReportCategory("hacks", "Hacks", Priority.HIGH, List.of("killaura"));
    private final PriorityCalculator calculator = new PriorityCalculator(
            () -> configWithEscalation(3, 600, Priority.HIGH), CLOCK);

    @Test
    void oneOffReportKeepsTheCategoryBasePriority() {
        Priority result = calculator.calculate(hacks, UUID.randomUUID(), List.of());

        assertEquals(Priority.HIGH, result);
    }

    @Test
    void belowThresholdReportsDoNotEscalate() {
        UUID target = UUID.randomUUID();
        List<Report> existing = List.of(pendingReport(target, "hacks", NOW.minusSeconds(60)));

        // 1 existing distinct reporter + the new one = 2, threshold is 3.
        Priority result = calculator.calculate(hacksLow(), UUID.randomUUID(), existing);

        assertEquals(Priority.LOW, result);
    }

    @Test
    void exactlyAtThresholdEscalates() {
        UUID target = UUID.randomUUID();
        List<Report> existing = List.of(pendingReport(target, "hacks", NOW.minusSeconds(60)),
                pendingReport(target, "hacks", NOW.minusSeconds(30)));

        // 2 existing distinct reporters + the new one = 3, meets the threshold.
        Priority result = calculator.calculate(hacksLow(), UUID.randomUUID(), existing);

        assertEquals(Priority.HIGH, result);
    }

    @Test
    void reportsOutsideTheWindowDoNotCountTowardsConsensus() {
        UUID target = UUID.randomUUID();
        List<Report> existing = List.of(pendingReport(target, "hacks", NOW.minusSeconds(60)),
                pendingReport(target, "hacks", NOW.minusSeconds(601)));

        Priority result = calculator.calculate(hacksLow(), UUID.randomUUID(), existing);

        assertEquals(Priority.LOW, result);
    }

    @Test
    void resolvedReportsDoNotCountTowardsConsensus() {
        UUID target = UUID.randomUUID();
        Report resolved = new Report(1, UUID.randomUUID(), UUID.randomUUID(), "Reporter", target, "Target", "hacks",
                null, null, "default", ReportStatus.RESOLVED_ACTION, Priority.HIGH, UUID.randomUUID(), "done",
                NOW.minusSeconds(60), NOW.minusSeconds(30), NOW.minusSeconds(10), 0, null);

        Priority result = calculator.calculate(hacksLow(), UUID.randomUUID(),
                List.of(resolved, pendingReport(target, "hacks", NOW.minusSeconds(60))));

        assertEquals(Priority.LOW, result);
    }

    @Test
    void differentCategoryDoesNotCountTowardsConsensus() {
        UUID target = UUID.randomUUID();
        List<Report> existing = List.of(pendingReport(target, "chat_abuse", NOW.minusSeconds(60)),
                pendingReport(target, "chat_abuse", NOW.minusSeconds(30)));

        Priority result = calculator.calculate(hacksLow(), UUID.randomUUID(), existing);

        assertEquals(Priority.LOW, result);
    }

    private ReportCategory hacksLow() {
        return new ReportCategory("hacks", "Hacks", Priority.LOW, List.of("killaura"));
    }

    private Report pendingReport(UUID target, String categoryId, Instant createdAt) {
        return new Report(1, UUID.randomUUID(), UUID.randomUUID(), "Reporter", target, "Target", categoryId, null, null,
                "default", ReportStatus.PENDING, Priority.LOW, null, null, createdAt, null, null, 0, null);
    }

    private BookReportsConfig configWithEscalation(int threshold, int windowSeconds, Priority escalateTo) {
        return new BookReportsConfig(dev.bookreports.config.StorageType.SQLITE,
                new dev.bookreports.config.MySqlSettings("localhost", 3306, "db", "root", "", 10), 120, 10, 300, true,
                true, java.util.Map.of("hacks", hacks),
                new PriorityEscalationSettings(threshold, windowSeconds, escalateTo),
                new dev.bookreports.config.FalseReportPenaltySettings(true, 3, 4, 0), false,
                new dev.bookreports.config.StaffSettings("ENTITY_EXPERIENCE_ORB_PICKUP", 15),
                new dev.bookreports.config.DiscordSettings(false, "", Priority.HIGH),
                new dev.bookreports.config.PunishmentSettings("7d", "1h"), true, "es_ES", "default");
    }
}
