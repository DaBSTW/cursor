CREATE TABLE IF NOT EXISTS br_staff_prefs (
    player_uuid            CHAR(36) PRIMARY KEY,
    notifications_enabled  INTEGER NOT NULL DEFAULT 1
);
