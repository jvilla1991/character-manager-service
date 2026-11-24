-- What a follower is, which decides who may create and dismiss it:
--   DM_GRANT   a companion the campaign's DM grants (every pre-V64 row)
--   SUMMON     a creature the player's own spell conjures (Summon Plant, ...)
--   FAMILIAR   the character's familiar; one per character, a new one replaces it
--   COMPANION  reserved for server-owned companions; nothing creates one yet
-- The DM alone grants and dismisses a DM_GRANT; the owning player (or that DM)
-- creates and dismisses a SUMMON or a FAMILIAR. The rules live in
-- PcFollowerService; the CHECK only keeps unknown values out. A kind never
-- changes once the row exists.
ALTER TABLE pc_follower
    ADD COLUMN kind TEXT NOT NULL DEFAULT 'DM_GRANT'
        CONSTRAINT pc_follower_kind_check
        CHECK (kind IN ('DM_GRANT', 'SUMMON', 'FAMILIAR', 'COMPANION'));
