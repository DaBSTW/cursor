-- Archiving hides an already-resolved report from the staff queue (findByStatus) without deleting it — the
-- report, its notes and its sanction audit trail all stay intact and reachable by direct id/uuid lookup.
-- 0/false by default: nothing is archived until staff explicitly do it.
ALTER TABLE br_reports ADD COLUMN archived INTEGER NOT NULL DEFAULT 0;
