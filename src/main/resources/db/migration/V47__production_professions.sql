-- Preserve existing grants while allowing production professions alongside gathering.
ALTER TABLE pc_profession DROP CONSTRAINT pc_profession_profession_check;
ALTER TABLE pc_profession ADD CONSTRAINT pc_profession_profession_check
    CHECK (profession IN (
        'archaeology', 'fishing', 'herbalism', 'mining', 'skinning',
        'alchemy', 'blacksmithing', 'cooking', 'enchanting', 'engineering',
        'inscription', 'jewelcrafting', 'leatherworking', 'tailoring'
    ));
