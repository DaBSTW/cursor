-- Auto-captured summary of the target's recent CoreProtect-logged block activity, attached the same way
-- chat_context is: without the reporter typing or copying anything. Nullable: only present when CoreProtect is
-- installed, enabled in config.yml, and actually has something on file for the target.
ALTER TABLE br_reports ADD COLUMN coreprotect_context VARCHAR(512);
