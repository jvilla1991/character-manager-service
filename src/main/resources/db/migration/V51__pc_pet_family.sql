-- World of Warcraft Hunter pet family: the family of a Beast Master hunter's Primal
-- Companion (e.g. "Wolf", "Bird of Prey"). Nullable — only a WoW Hunter (ranger with a
-- Beast Mastery/Marksmanship/Survival specialization) whose subclass is Beast Master may
-- set it, and the allowed names are validated in the service layer — no DB CHECK here.
ALTER TABLE pc ADD COLUMN pet_family VARCHAR(40);
