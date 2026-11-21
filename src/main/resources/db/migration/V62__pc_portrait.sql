-- Character portraits: an image the owner (or the campaign's DM) uploads for a
-- character. One row per character, upserted on each upload, holding the raw
-- bytes and the content type they were sent with (image/jpeg, image/png or
-- image/webp — checked in PcPortraitService, no DB CHECK here). Kept in its own
-- table so loading a PC never drags the image bytes along. ON DELETE CASCADE:
-- deleting a character removes its portrait with it.
CREATE TABLE IF NOT EXISTS pc_portrait (
    pc_id        BIGINT PRIMARY KEY REFERENCES pc (id) ON DELETE CASCADE,
    content_type VARCHAR(40) NOT NULL,
    image        BYTEA NOT NULL,
    updated_at   TIMESTAMP NOT NULL
);

-- Server-owned cache key for the portrait, sent to clients as portraitVersion
-- (they fetch GET /api/v1/pc/{id}/portrait?v=<version>). NULL = no portrait.
-- Only the portrait endpoints change it; it moves to a new value on every
-- upload, so a cached old image is never shown under a new version.
ALTER TABLE pc ADD COLUMN portrait_version BIGINT;
