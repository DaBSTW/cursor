CREATE TABLE IF NOT EXISTS br_staff_prefs (
    player_uuid            CHAR(36) PRIMARY KEY,
    notifications_enabled  TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
