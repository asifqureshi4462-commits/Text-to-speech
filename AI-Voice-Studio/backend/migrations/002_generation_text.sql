-- Keep the full submitted script with each generation so a history entry can be regenerated exactly.
-- Private to the owning user; removed with the generation and with the account (ON DELETE CASCADE on user).
ALTER TABLE generations ADD COLUMN text TEXT;
