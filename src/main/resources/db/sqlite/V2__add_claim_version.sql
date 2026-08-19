-- Optimistic-locking counter, bumped on every claim. Used by the staff panel (Fase 5) to detect
-- two staff members resolving the same report at once.
ALTER TABLE br_reports ADD COLUMN claim_version INTEGER NOT NULL DEFAULT 0;
