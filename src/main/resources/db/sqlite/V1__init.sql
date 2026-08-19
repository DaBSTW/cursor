CREATE TABLE IF NOT EXISTS br_reports (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid            CHAR(36)      NOT NULL UNIQUE,
    reporter_uuid   CHAR(36)      NOT NULL,
    reporter_name   VARCHAR(16)   NOT NULL,
    target_uuid     CHAR(36)      NOT NULL,
    target_name     VARCHAR(16)   NOT NULL,
    category_id     VARCHAR(32)   NOT NULL,
    sub_reason_id   VARCHAR(32),
    evidence_text   VARCHAR(256),
    server          VARCHAR(64)   NOT NULL DEFAULT 'default',
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    priority        VARCHAR(10)   NOT NULL DEFAULT 'LOW',
    reviewer_uuid   CHAR(36),
    resolution_note VARCHAR(256),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at      TIMESTAMP,
    resolved_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_br_reports_target   ON br_reports(target_uuid);
CREATE INDEX IF NOT EXISTS idx_br_reports_status   ON br_reports(status);
CREATE INDEX IF NOT EXISTS idx_br_reports_reporter ON br_reports(reporter_uuid, created_at);

CREATE TABLE IF NOT EXISTS br_report_penalties (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid   CHAR(36)   NOT NULL,
    reason        VARCHAR(64) NOT NULL,
    applied_at    TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_br_penalties_player ON br_report_penalties(player_uuid);
