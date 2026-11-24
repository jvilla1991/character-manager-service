package com.moo.charactermanagerservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;
import java.util.Map;

/**
 * Optional player choices supplied when committing a level-up. The body is optional — levels
 * with no choices send nothing.
 *
 * <p>Phase 3: {@code subclass} (the chosen subclass name when a class reaches its
 * subclass-selection level). Phase 4: {@code abilityIncreases} — the Ability Score Improvement
 * allocation as {@code ABILITY -> points}, e.g. {@code {"CON":2}} or {@code {"STR":1,"DEX":1}}.
 * Feats: {@code feat} — the chosen General feat name, the alternative to an ASI at an ASI level
 * (exactly one of {@code abilityIncreases} / {@code feat} is supplied there).
 *
 * <p>Spells: {@code newSpells} — cantrips/spells the caster learns this level, each a spell object
 * ({@code lvl}, {@code name}, …) built by the SPA from its spell list. The server validates the
 * <em>count</em> against the level-up delta, the <em>spell level</em> against the slot table at
 * the new level (no learning spells you have no slots for), and rejects duplicates; it does not
 * validate individual spell names, because the spell list lives in the frontend (the backend must
 * not depend on the external D&D API). Same trust posture as feats/subclasses.
 *
 * <p>HP: {@code hpMode} — whether this level's hit points use the fixed average or a roll. It is
 * optional; {@code null} (or an omitted field) means {@link HpMode#AVERAGE}, so existing clients are
 * unaffected. In {@link HpMode#ROLL} the server rolls the die — the client never sends a roll
 * result, only the choice of mode.
 *
 * <p>Specialization: {@code specialization} — an optional new hero-class specialization (e.g. a
 * Death Knight re-Runeforging to {@code "Frost"}). {@code null} keeps the current one. Only valid
 * for a hero-subclass character, only one of that subclass's values, and only a <em>change</em>
 * when the subclass allows respec on level-up (the Ebon Blade does; the Demon Hunter does not —
 * a Demon Hunter may only pick one here while none is set). The old JSON name {@code rune} is
 * still accepted as an alias for SPA builds deployed before V46.
 *
 * <p>Fighting Style: {@code fightingStyle} — optional choice at the class's Fighting Style level
 * (Paladin/Ranger 2), one of the preview's {@code fightingStyleOptions}. Choosing
 * {@code "Void Warrior"} (Demon Hunter only) requires exactly two cantrips (level 0) in
 * {@code newSpells}; so do the other cantrip Fighting Styles, {@code "Arcane Warrior"} included.
 *
 * <p>Feat spell picks: a feat that lets the player choose spells ({@code "Arcane Warrior"} taken
 * as a feat, {@code "Magic Connoisseur"}, {@code "Boon of Magic School Mastery (…)"}) needs those
 * spells in {@code newSpells}, each with the feat's name as its {@code grantedBy}. That marker is
 * how they are told from the class's own picks: they sit outside the class counts and the slot
 * cap, and are checked against the feat's own rule (number, spell level, school).
 */
public record LevelUpRequest(
        String subclass,
        Map<String, Integer> abilityIncreases,
        String feat,
        List<Map<String, Object>> newSpells,
        HpMode hpMode,
        @JsonAlias("rune") String specialization,
        String fightingStyle
) {
    /** Back-compat constructor for callers that don't choose an HP mode: defaults to AVERAGE. */
    public LevelUpRequest(String subclass, Map<String, Integer> abilityIncreases,
                          String feat, List<Map<String, Object>> newSpells) {
        this(subclass, abilityIncreases, feat, newSpells, HpMode.AVERAGE, null, null);
    }

    /** Back-compat constructor for callers that don't change the specialization. */
    public LevelUpRequest(String subclass, Map<String, Integer> abilityIncreases,
                          String feat, List<Map<String, Object>> newSpells, HpMode hpMode) {
        this(subclass, abilityIncreases, feat, newSpells, hpMode, null, null);
    }

    /** Back-compat constructor for callers that don't choose a Fighting Style. */
    public LevelUpRequest(String subclass, Map<String, Integer> abilityIncreases,
                          String feat, List<Map<String, Object>> newSpells, HpMode hpMode,
                          String specialization) {
        this(subclass, abilityIncreases, feat, newSpells, hpMode, specialization, null);
    }
}
