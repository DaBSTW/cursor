-- Auto-captured chat context (last few lines said by the target before the report), attached without the
-- reporter having to type anything. Nullable: only present when the reporter had chat history buffered.
ALTER TABLE br_reports ADD COLUMN chat_context VARCHAR(512);
