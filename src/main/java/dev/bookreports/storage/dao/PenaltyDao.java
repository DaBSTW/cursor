package dev.bookreports.storage.dao;

import dev.bookreports.storage.model.ReportPenalty;
import java.time.Instant;
import java.util.UUID;

public interface PenaltyDao {

    ReportPenalty insert(ReportPenalty penalty);

    int countByPlayerSince(UUID playerUuid, String reason, Instant since);
}
