-- Voice IDs belong to a provider; remember which one a favourite was saved for.
ALTER TABLE favorite_voices ADD COLUMN provider TEXT NOT NULL DEFAULT 'elevenlabs';
