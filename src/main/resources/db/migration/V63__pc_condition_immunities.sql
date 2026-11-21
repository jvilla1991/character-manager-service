-- The conditions a character is immune to, kept beside the conditions it has
-- (pc.conditions). Same shape as that column: a JSON array of condition names,
--   ["poisoned","charmed"]
-- The sheet shows a condition the character has in red and one it is immune to
-- in green. NULL = none yet. A condition is never in both columns: the service
-- layer (ConditionRules) drops an immunity whose condition is active.
ALTER TABLE pc ADD COLUMN condition_immunities TEXT;
