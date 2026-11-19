-- Species origin for World of Warcraft species (create wizard): which content source the
-- species comes from ("World of Warcraft"), the lineage chosen for it ("Night Elf",
-- "Taunka") and its size ("Small" / "Medium" — a Forsaken or Human chooses, a Goblin is
-- always Small). All three are nullable and NULL for every existing character and for
-- base-game species, whose size the client still reads from the species name.
-- Free text like pc.species itself: the allowed values live in the client's species
-- catalog, so there is no CHECK and no length limit here.
ALTER TABLE pc ADD COLUMN species_source TEXT;
ALTER TABLE pc ADD COLUMN lineage TEXT;
ALTER TABLE pc ADD COLUMN size TEXT;
