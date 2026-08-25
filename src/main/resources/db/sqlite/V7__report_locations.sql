-- Snapshot of both players' positions at submission time, encoded by LocationCodec as a single delimited
-- string ("world;x;y;z;yaw;pitch") — same compact pattern as chat_context/coreprotect_context. Nullable:
-- target_location is naturally absent when the target was offline at submission time (see ReportCommand's
-- offline-reporting path); either can also fail to decode later if its world was since removed.
ALTER TABLE br_reports ADD COLUMN target_location VARCHAR(160);
ALTER TABLE br_reports ADD COLUMN reporter_location VARCHAR(160);
