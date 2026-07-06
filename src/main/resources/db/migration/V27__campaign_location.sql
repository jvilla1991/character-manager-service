-- Party location for a campaign, set by the DM in Session Mode and shown at the
-- top of every member's character sheet. JSON: {"name":"Neverwinter","type":"Settlement"}.
-- NULL until the DM sets it. Type is one of Settlement | Wilderness | Dungeon.
ALTER TABLE campaign ADD COLUMN IF NOT EXISTS location TEXT;
