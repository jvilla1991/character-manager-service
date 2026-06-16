package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.progression.ClassProgression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Server-authoritative level-up rules engine (single-class, D&D 5e 2024).
 *
 * <p><strong>Single responsibility:</strong> compute the deterministic gains of advancing one
 * level. It does not load, persist, or authorize — {@link PCService} owns that and delegates
 * here. Keeping the rules out of the controller and out of PCService means the math is unit
 * tested in isolation (no repo, no Spring context, no security).
 *
 * <p><strong>HP policy (Phase 1):</strong> fixed average — {@code averageHitDieValue + CON mod},
 * floored at {@value #MIN_HP_PER_LEVEL} per level (5e minimum). Rolled HP is a future seam: it
 * would swap the {@link #hpGain} strategy without touching callers.
 *
 * <p><strong>Multiclass seam:</strong> today total level == single class level, so hit die keys
 * off {@code pc.clazz} and proficiency bonus off {@code pc.level}. When multiclassing lands,
 * hit-die/feature lookups move to {@code (class, classLevel)} while proficiency bonus stays on
 * total level — only the inputs change, not this contract.
 */
@Service
public class LevelUpService {

    /** A character always gains at least this much HP per level, even with a CON penalty. */
    private static final int MIN_HP_PER_LEVEL = 1;

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
                ClassProgression.proficiencyBonusForLevel(newLevel)
        );
    }

    /**
     * Mutate the given (managed) PC in place to its next level: bump level, add the HP gain to
     * both max and current HP, and recompute the proficiency bonus. The caller persists.
     */
    public PC applyLevelUp(PC pc) {
        int currentLevel = currentLevel(pc);
        assertCanLevelUp(currentLevel);

        int newLevel = currentLevel + 1;
        int hitDie = ClassProgression.hitDie(pc.getClazz());
        int conMod = ClassProgression.abilityModifier(nz(pc.getAbilityCon()));
        int hpGained = hpGain(hitDie, conMod);

        pc.setLevel((short) newLevel);
        pc.setHpMax((short) (nz(pc.getHpMax()) + hpGained));
        pc.setHpCurrent((short) (nz(pc.getHpCurrent()) + hpGained));
        pc.setProfBonus((short) ClassProgression.proficiencyBonusForLevel(newLevel));
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

    /** Null-safe read of a nullable Short stat as a primitive (older PCs may have null combat stats). */
    private static int nz(Short value) {
        return value == null ? 0 : value;
    }
}
