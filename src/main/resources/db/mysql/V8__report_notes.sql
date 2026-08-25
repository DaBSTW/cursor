-- Free-text notes staff can leave on a report at any point in its lifetime — independent of, and in addition
-- to, the single resolution_note set once when a report is closed. See ReportNote.
CREATE TABLE IF NOT EXISTS br_report_notes (
    id          INT          AUTO_INCREMENT PRIMARY KEY,
    report_id   INT          NOT NULL,
    author_uuid CHAR(36)     NOT NULL,
    author_name VARCHAR(16)  NOT NULL,
    note_text   VARCHAR(256) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_br_report_notes_report (report_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
