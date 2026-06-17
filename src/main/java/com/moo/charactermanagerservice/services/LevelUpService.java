package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.progression.ClassProgression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Server-authoritative level-up rules engine (single-class, D&D 5e 2024).
 *
 * <p><strong>Single responsibility:</strong> compute the deterministic gains of advancing one
 * level. It does not load, persist, or authorize — {@link PCService} owns that and delegates
 * here. Keeping the rules out of the controller and out of PCService means the math is unit
 * tested in isolation (no repo, no Spring context, no security).
 *
 * <p><strong>HP policy (Phase 1):</strong> fixed average — {@code averageHitDieValue + CON mod},
 * floored at {@value #MIN_HP_PER_LEVEL} per level (5e minimum). Rolled HP is a future seam.
 *
 * <p><strong>Spell slots (Phase 2):</strong> for caster classes, the {@code spellSlots} JSON-as-TEXT
 * column is rebuilt from {@link ClassProgression#spellSlotsFor} — {@code max} is set from the table
 * for the new level while the player's {@code used} count is preserved (clamped to the new max).
 * Non-casters are left untouched. This is the first phase that parses the JSON columns, so it is
 * defensive about null/blank/malformed values.
 *
 * <p><strong>Multiclass seam:</strong> today total level == single class level, so all tables key
 * off {@code pc.clazz} / {@code pc.level}. When multiclassing lands, hit-die/slot lookups move to
 * {@code (class, classLevel)} (slots via the multiclass caster-level rule) while proficiency bonus
 * stays on total level — only the inputs change, not this contract.
 */
@Service
public class LevelUpService {

    /** A character always gains at least this much HP per level, even with a CON penalty. */
    private static final int MIN_HP_PER_LEVEL = 1;

    private final ObjectMapper objectMapper;

    public LevelUpService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Compute — without persisting — what advancing one level would grant. */
    public LevelUpPreview preview(PC pc) {
        int currentLevel = currentLevel(pc);
        assertCanLevelUp(currentLevel);

        int newLevel = currentLevel + 1;
        int hitDie = ClassProgression.hitDie(pc.getClazz());
        int conMod = ClassProgression.abilityModifier(nz(pc.getAbilityCon()));
        int hpGained = hpGain(hitDie, conMod);

        return new LevelUpPreview(
                currentLevel,
                newLevel,
                hitDie,
                conMod,
                hpGained,
                nz(pc.getHpMax()) + hpGained,
                ClassProgression.proficiencyBonusForLevel(currentLevel),
                ClassProgression.proficiencyBonusForLevel(newLevel),
                currentMaxSlots(pc),
                ClassProgression.spellSlotsFor(pc.getClazz(), newLevel),
                subclassDue(pc, newLevel),
                ClassProgression.subclassesFor(pc.getClazz())
        );
    }

    /** Convenience overload for level-ups with no player choices. */
    public PC applyLevelUp(PC pc) {
        return applyLevelUp(pc, null);
    }

    /**
     * Mutate the given (managed) PC in place to its next level: bump level, add the HP gain to
     * both max and current HP, recompute the proficiency bonus, rebuild the spell-slot map (for
     * casters), and apply the chosen subclass when one is due. The caller persists.
     *
     * @param chosenSubclass the player's subclass selection, or {@code null} when none applies
     */
    public PC applyLevelUp(PC pc, String chosenSubclass) {
        int currentLevel = currentLevel(pc);
        assertCanLevelUp(currentLevel);

        int newLevel = currentLevel + 1;
        int hitDie = ClassProgression.hitDie(pc.getClazz());
        int conMod = ClassProgression.abilityModifier(nz(pc.getAbilityCon()));
        int hpGained = hpGain(hitDie, conMod);

        // Validate the subclass choice against the new level before mutating anything.
        applySubclassChoice(pc, newLevel, chosenSubclass);

        pc.setLevel((short) newLevel);
        pc.setHpMax((short) (nz(pc.getHpMax()) + hpGained));
        pc.setHpCurrent((short) (nz(pc.getHpCurrent()) + hpGained));
        pc.setProfBonus((short) ClassProgression.proficiencyBonusForLevel(newLevel));

        if (ClassProgression.isCaster(pc.getClazz())) {
            pc.setSpellSlots(rebuildSpellSlots(pc, newLevel));
        }
        return pc;
    }

    private int hpGain(int hitDie, int conMod) {
        return Math.max(MIN_HP_PER_LEVEL, ClassProgression.averageHitDieValue(hitDie) + conMod);
    }

    private int currentLevel(PC pc) {
        return pc.getLevel() == null ? 1 : pc.getLevel();
    }

    private void assertCanLevelUp(int currentLevel) {
        if (currentLevel >= ClassProgression.MAX_LEVEL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Character is already at the maximum level (" + ClassProgression.MAX_LEVEL + ")");
        }
    }

    // ── Subclass selection (Phase 3) ─────────────────────────────────────────────

    /** A subclass choice is due when the new level is the class's grant level and none is set yet. */
    private boolean subclassDue(PC pc, int newLevel) {
        return newLevel == ClassProgression.subclassLevelFor(pc.getClazz())
                && isBlank(pc.getSubclass());
    }

    /**
     * Validate and apply the player's subclass choice for this level. Server-authoritative:
     * a choice is only accepted when it is actually due, and (once catalog content exists) it
     * must be a known subclass for the class. With the catalog empty, the flow is dormant — no
     * choice is required and none is offered — so today this is effectively a no-op.
     */
    private void applySubclassChoice(PC pc, int newLevel, String chosen) {
        boolean due = subclassDue(pc, newLevel);
        List<String> options = ClassProgression.subclassesFor(pc.getClazz());

        if (chosen != null && !chosen.isBlank()) {
            String choice = chosen.trim();
            if (!due) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A subclass cannot be selected for " + pc.getClazz() + " at level " + newLevel);
            }
            if (!options.isEmpty() && options.stream().noneMatch(o -> o.equalsIgnoreCase(choice))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "'" + choice + "' is not a valid subclass for " + pc.getClazz());
            }
            pc.setSubclass(choice);
            return;
        }

        // No choice supplied: only an error when a real catalog exists and a pick is due.
        if (due && !options.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A subclass must be selected at level " + newLevel);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ── Spell-slot handling ──────────────────────────────────────────────────────

    /**
     * Rebuild the {@code spellSlots} JSON for the new level: {@code max} comes from the class
     * table, {@code used} is carried over from the current slots (clamped to the new max), and
     * spell levels newly unlocked start at {@code used = 0}. Ordered by spell level.
     */
    private String rebuildSpellSlots(PC pc, int newLevel) {
        Map<Integer, Integer> target = ClassProgression.spellSlotsFor(pc.getClazz(), newLevel);
        Map<Integer, SlotState> existing = parseSlots(pc.getSpellSlots());

        Map<String, Map<String, Integer>> rebuilt = new LinkedHashMap<>();
        target.forEach((spellLevel, max) -> {
            SlotState prior = existing.get(spellLevel);
            int used = prior == null ? 0 : Math.min(prior.used(), max);
            Map<String, Integer> entry = new LinkedHashMap<>();
            entry.put("max", max);
            entry.put("used", used);
            rebuilt.put(String.valueOf(spellLevel), entry);
        });

        try {
            return objectMapper.writeValueAsString(rebuilt);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize spell slots");
        }
    }

    /** Current {@code spellLevel -> max} for the preview (sorted; empty if none/malformed). */
    private Map<Integer, Integer> currentMaxSlots(PC pc) {
        Map<Integer, Integer> out = new TreeMap<>();
        parseSlots(pc.getSpellSlots()).forEach((lvl, state) -> out.put(lvl, state.max()));
        return out;
    }

    /**
     * Parse the {@code spellSlots} TEXT column into {@code spellLevel -> {max, used}}. Defensive:
     * null, blank, or malformed JSON yields an empty map rather than failing the level-up.
     */
    private Map<Integer, SlotState> parseSlots(String json) {
        Map<Integer, SlotState> out = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return out;
        try {
            Map<String, Map<String, Integer>> raw =
                    objectMapper.readValue(json, new TypeReference<>() {});
            raw.forEach((lvl, state) -> {
                try {
                    int max = state.getOrDefault("max", 0);
                    int used = state.getOrDefault("used", 0);
                    out.put(Integer.parseInt(lvl), new SlotState(max, used));
                } catch (NumberFormatException ignored) {
                    // skip non-numeric spell-level keys
                }
            });
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>(); // malformed column — treat as no slots
        }
        return out;
    }

    private record SlotState(int max, int used) {}

    /** Null-safe read of a nullable Short stat as a primitive (older PCs may have null combat stats). */
    private static int nz(Short value) {
        return value == null ? 0 : value;
    }
}
