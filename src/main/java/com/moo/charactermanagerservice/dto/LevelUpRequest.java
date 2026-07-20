package com.moo.charactermanagerservice.dto;

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
 */
public record LevelUpRequest(
        String subclass,
        Map<String, Integer> abilityIncreases,
        String feat,
        List<Map<String, Object>> newSpells,
        HpMode hpMode
) {
    /** Back-compat constructor for callers that don't choose an HP mode: defaults to AVERAGE. */
    public LevelUpRequest(String subclass, Map<String, Integer> abilityIncreases,
                          String feat, List<Map<String, Object>> newSpells) {
        this(subclass, abilityIncreases, feat, newSpells, HpMode.AVERAGE);
    }
}
