package dev.bookreports.storage.dao;

import dev.bookreports.storage.StorageException;
import dev.bookreports.storage.model.Priority;
import dev.bookreports.storage.model.Report;
import dev.bookreports.storage.model.ReportStatus;
import dev.bookreports.storage.model.ReporterStats;
import dev.bookreports.storage.model.StaffStats;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** ANSI-compatible JDBC implementation of {@link ReportDao}, works against both the SQLite and MySQL dialects. */
public final class JdbcReportDao implements ReportDao {

    private final DataSource dataSource;

    public JdbcReportDao(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Report insert(Report report) {
        String sql = "INSERT INTO br_reports (uuid, reporter_uuid, reporter_name, target_uuid, target_name, "
                + "category_id, sub_reason_id, evidence_text, server, status, priority, created_at, chat_context, "
                + "coreprotect_context, target_location, reporter_location) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        long id;
        // The insert connection must be closed before findById below borrows another one — the pool is
        // sized to 1 for SQLite, so holding both open at once would deadlock waiting for itself.
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, report.uuid().toString());
            statement.setString(2, report.reporterUuid().toString());
            statement.setString(3, report.reporterName());
            statement.setString(4, report.targetUuid().toString());
            statement.setString(5, report.targetName());
            statement.setString(6, report.categoryId());
            statement.setString(7, report.subReasonId());
            statement.setString(8, report.evidenceText());
            statement.setString(9, report.server());
            statement.setString(10, report.status().name());
            statement.setString(11, report.priority().name());
            statement.setTimestamp(12, Timestamp.from(report.createdAt()));
            statement.setString(13, report.chatContext());
            statement.setString(14, report.coreProtectContext());
            statement.setString(15, report.targetLocation());
            statement.setString(16, report.reporterLocation());
            statement.executeUpdate();
            id = generatedId(statement, report.uuid());
        } catch (SQLException e) {
            throw new StorageException("Failed to insert report for target=" + report.targetUuid(), e);
        }
        return findById(id)
                .orElseThrow(() -> new StorageException("Inserted report id=" + id + " could not be re-read"));
    }

    private long generatedId(Statement statement, UUID reportUuid) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new StorageException("Insert did not return a generated id for report uuid=" + reportUuid);
            }
            return keys.getLong(1);
        }
    }

    @Override
    public Optional<Report> findByUuid(UUID uuid) {
        String sql = "SELECT * FROM br_reports WHERE uuid = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            return queryOne(statement);
        } catch (SQLException e) {
            throw new StorageException("Failed to read report uuid=" + uuid, e);
        }
    }

    @Override
    public Optional<Report> findById(long id) {
        String sql = "SELECT * FROM br_reports WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return queryOne(statement);
        } catch (SQLException e) {
            throw new StorageException("Failed to read report id=" + id, e);
        }
    }

    private Optional<Report> queryOne(PreparedStatement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery()) {
            return rs.next() ? Optional.of(map(rs)) : Optional.empty();
        }
    }

    @Override
    public List<Report> findByTarget(UUID targetUuid) {
        String sql = "SELECT * FROM br_reports WHERE target_uuid = ? ORDER BY created_at DESC";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetUuid.toString());
            return queryList(statement);
        } catch (SQLException e) {
            throw new StorageException("Failed to read report history for target=" + targetUuid, e);
        }
    }

    @Override
    public List<Report> findByStatus(ReportStatus status, int page, int pageSize) {
        return findByStatus(status, null, page, pageSize);
    }

    @Override
    public List<Report> findByStatus(ReportStatus status, String categoryId, int page, int pageSize) {
        return findByStatus(status, categoryId, null, null, null, page, pageSize);
    }

    @Override
    public List<Report> findByStatus(ReportStatus status, String categoryId, Priority priority, String targetNameQuery,
            UUID claimedBy, int page, int pageSize) {
        if (page < 0 || pageSize < 1) {
            throw new IllegalArgumentException("page must be >= 0 and pageSize must be >= 1");
        }
        // Priority is stored as text, so a plain ORDER BY priority would sort alphabetically (HIGH, LOW,
        // MEDIUM) instead of by severity — this CASE expression ranks it HIGH, MEDIUM, LOW instead.
        // archived = 0 is unconditional here, not an optional filter: an archived report is meant to disappear
        // from every staff-queue view, still reachable only via findById/findByUuid direct lookup.
        StringBuilder sql = new StringBuilder("SELECT * FROM br_reports WHERE status = ? AND archived = 0");
        if (categoryId != null) {
            sql.append(" AND category_id = ?");
        }
        if (priority != null) {
            sql.append(" AND priority = ?");
        }
        if (targetNameQuery != null) {
            // UPPER(...) LIKE UPPER(?) is portable across SQLite and MySQL, unlike ILIKE (Postgres-only).
            sql.append(" AND UPPER(target_name) LIKE UPPER(?)");
        }
        if (claimedBy != null) {
            sql.append(" AND reviewer_uuid = ?");
        }
        sql.append(" ORDER BY CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 WHEN 'LOW' THEN 2 ELSE 3 END, "
                + "created_at ASC LIMIT ? OFFSET ?");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            statement.setString(index++, status.name());
            if (categoryId != null) {
                statement.setString(index++, categoryId);
            }
            if (priority != null) {
                statement.setString(index++, priority.name());
            }
            if (targetNameQuery != null) {
                statement.setString(index++, "%" + targetNameQuery + "%");
            }
            if (claimedBy != null) {
                statement.setString(index++, claimedBy.toString());
            }
            statement.setInt(index++, pageSize);
            statement.setInt(index, page * pageSize);
            return queryList(statement);
        } catch (SQLException e) {
            throw new StorageException("Failed to read report queue for status=" + status, e);
        }
    }

    @Override
    public List<Report> findByReporter(UUID reporterUuid, int limit) {
        String sql = "SELECT * FROM br_reports WHERE reporter_uuid = ? ORDER BY created_at DESC LIMIT ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reporterUuid.toString());
            statement.setInt(2, limit);
            return queryList(statement);
        } catch (SQLException e) {
            throw new StorageException("Failed to read own reports for reporter=" + reporterUuid, e);
        }
    }

    private List<Report> queryList(PreparedStatement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery()) {
            List<Report> results = new ArrayList<>();
            while (rs.next()) {
                results.add(map(rs));
            }
            return List.copyOf(results);
        }
    }

    @Override
    public int countByReporterSince(UUID reporterUuid, Instant since) {
        String sql = "SELECT COUNT(*) FROM br_reports WHERE reporter_uuid = ? AND created_at >= ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reporterUuid.toString());
            statement.setTimestamp(2, Timestamp.from(since));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to count reports for reporter=" + reporterUuid, e);
        }
    }

    @Override
    public int countByStatus(ReportStatus status) {
        String sql = "SELECT COUNT(*) FROM br_reports WHERE status = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to count reports for status=" + status, e);
        }
    }

    @Override
    public boolean updateStatus(long id, ReportStatus status, UUID reviewerUuid, String resolutionNote,
            Instant resolvedAt) {
        // The status guard is the double-resolution defense: if two staff resolve the same report at once,
        // only the first UPDATE matches (still PENDING/IN_REVIEW) — the second affects zero rows and the
        // caller sees `false` instead of silently overwriting the first resolution.
        String sql = "UPDATE br_reports SET status = ?, reviewer_uuid = ?, resolution_note = ?, resolved_at = ? "
                + "WHERE id = ? AND status IN ('PENDING', 'IN_REVIEW')";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            boolean resolved = status != ReportStatus.PENDING && status != ReportStatus.IN_REVIEW;
            statement.setString(1, status.name());
            statement.setString(2, reviewerUuid != null ? reviewerUuid.toString() : null);
            statement.setString(3, resolutionNote);
            statement.setTimestamp(4, resolved ? Timestamp.from(resolvedAt) : null);
            statement.setLong(5, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StorageException("Failed to update status for report id=" + id, e);
        }
    }

    @Override
    public boolean recordSanction(long id, String sanctionType, String sanctionDuration) {
        String sql = "UPDATE br_reports SET sanction_type = ?, sanction_duration = ? WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sanctionType);
            statement.setString(2, sanctionDuration);
            statement.setLong(3, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StorageException("Failed to record sanction for report id=" + id, e);
        }
    }

    @Override
    public boolean claim(long id, UUID reviewerUuid, Instant claimedAt) {
        String sql = "UPDATE br_reports SET reviewer_uuid = ?, claimed_at = ?, status = ?, "
                + "claim_version = claim_version + 1 WHERE id = ? AND reviewer_uuid IS NULL";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reviewerUuid.toString());
            statement.setTimestamp(2, Timestamp.from(claimedAt));
            statement.setString(3, ReportStatus.IN_REVIEW.name());
            statement.setLong(4, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StorageException("Failed to claim report id=" + id, e);
        }
    }

    @Override
    public List<Report> findStaleClaims(Instant claimedBefore) {
        String sql = "SELECT * FROM br_reports WHERE status = ? AND claimed_at < ? ORDER BY claimed_at ASC";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ReportStatus.IN_REVIEW.name());
            statement.setTimestamp(2, Timestamp.from(claimedBefore));
            return queryList(statement);
        } catch (SQLException e) {
            throw new StorageException("Failed to read stale claims before=" + claimedBefore, e);
        }
    }

    @Override
    public boolean releaseClaim(long id, UUID reviewerUuid) {
        String sql = "UPDATE br_reports SET reviewer_uuid = NULL, claimed_at = NULL, status = ? "
                + "WHERE id = ? AND reviewer_uuid = ? AND status = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ReportStatus.PENDING.name());
            statement.setLong(2, id);
            statement.setString(3, reviewerUuid.toString());
            statement.setString(4, ReportStatus.IN_REVIEW.name());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StorageException("Failed to release claim for report id=" + id, e);
        }
    }

    @Override
    public ReporterStats reporterStats(UUID reporterUuid) {
        // RESOLVED_DUPLICATE is deliberately excluded from both buckets: someone else already reported the
        // same thing first, which says nothing about whether this reporter was right.
        String sql = "SELECT COUNT(*) AS total, "
                + "SUM(CASE WHEN status = 'RESOLVED_ACTION' THEN 1 ELSE 0 END) AS actioned, "
                + "SUM(CASE WHEN status IN ('RESOLVED_REJECTED', 'FALSE_REPORT') THEN 1 ELSE 0 END) AS rejected "
                + "FROM br_reports WHERE reporter_uuid = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reporterUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return new ReporterStats(rs.getInt("total"), rs.getInt("actioned"), rs.getInt("rejected"));
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to compute reporter stats for reporter=" + reporterUuid, e);
        }
    }

    @Override
    public StaffStats staffStats(UUID reviewerUuid) {
        // Averaging claim-to-resolution time in SQL would need dialect-specific date arithmetic (julianday()
        // vs TIMESTAMPDIFF()), so the durations are computed here instead, keeping this class portable.
        String sql = "SELECT claimed_at, resolved_at FROM br_reports "
                + "WHERE reviewer_uuid = ? AND resolved_at IS NOT NULL";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reviewerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                int resolvedCount = 0;
                long totalSeconds = 0;
                int timedCount = 0;
                while (rs.next()) {
                    resolvedCount++;
                    Timestamp claimedAt = rs.getTimestamp("claimed_at");
                    Timestamp resolvedAt = rs.getTimestamp("resolved_at");
                    if (claimedAt != null) {
                        totalSeconds += Duration.between(claimedAt.toInstant(), resolvedAt.toInstant()).getSeconds();
                        timedCount++;
                    }
                }
                double avgMinutes = timedCount == 0 ? 0.0 : totalSeconds / 60.0 / timedCount;
                return new StaffStats(resolvedCount, avgMinutes);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to compute staff stats for reviewer=" + reviewerUuid, e);
        }
    }

    @Override
    public boolean setArchived(long id, boolean archived) {
        String sql = "UPDATE br_reports SET archived = ? WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, archived);
            statement.setLong(2, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StorageException("Failed to set archived=" + archived + " for report id=" + id, e);
        }
    }

    @Override
    public boolean purge(long id) {
        // Both statements run on the same connection so the pool-size-1 SQLite setup used elsewhere in this
        // class never deadlocks waiting for a second connection to itself.
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement deleteNotes = connection
                    .prepareStatement("DELETE FROM br_report_notes WHERE report_id = ?")) {
                deleteNotes.setLong(1, id);
                deleteNotes.executeUpdate();
            }
            try (PreparedStatement deleteReport = connection.prepareStatement("DELETE FROM br_reports WHERE id = ?")) {
                deleteReport.setLong(1, id);
                return deleteReport.executeUpdate() == 1;
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to purge report id=" + id, e);
        }
    }

    private Report map(ResultSet rs) throws SQLException {
        Timestamp claimedAt = rs.getTimestamp("claimed_at");
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        String reviewerUuid = rs.getString("reviewer_uuid");
        return new Report(rs.getLong("id"), UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("reporter_uuid")), rs.getString("reporter_name"),
                UUID.fromString(rs.getString("target_uuid")), rs.getString("target_name"), rs.getString("category_id"),
                rs.getString("sub_reason_id"), rs.getString("evidence_text"), rs.getString("server"),
                ReportStatus.valueOf(rs.getString("status")), Priority.valueOf(rs.getString("priority")),
                reviewerUuid != null ? UUID.fromString(reviewerUuid) : null, rs.getString("resolution_note"),
                rs.getTimestamp("created_at").toInstant(), claimedAt != null ? claimedAt.toInstant() : null,
                resolvedAt != null ? resolvedAt.toInstant() : null, rs.getInt("claim_version"),
                rs.getString("chat_context"), rs.getString("sanction_type"), rs.getString("sanction_duration"),
                rs.getString("coreprotect_context"), rs.getString("target_location"), rs.getString("reporter_location"),
                rs.getBoolean("archived"));
    }
}
