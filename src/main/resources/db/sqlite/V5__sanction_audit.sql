-- Structured record of what was actually applied via the punishment bridge (kick/mute/ban + duration), independent
-- of the free-text resolution note. Both nullable: only set for reports resolved through the sanction menu.
ALTER TABLE br_reports ADD COLUMN sanction_type VARCHAR(16);
ALTER TABLE br_reports ADD COLUMN sanction_duration VARCHAR(32);
