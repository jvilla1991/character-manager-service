package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.HpMode;
import com.moo.charactermanagerservice.progression.ClassProgression;

import java.util.random.RandomGenerator;

/**
 * HP-per-level policy for {@link LevelUpService}. Owns the RNG seam so rolled HP is the only
 * randomness in the engine and can be made deterministic in tests: in {@link HpMode#ROLL} the die
 * is rolled here, on the server, so the client never supplies a roll result and cannot inflate HP.
 */
class HpCalculator {

    /** A character always gains at least this much HP per level, even with a CON penalty. */
    private static final int MIN_HP_PER_LEVEL = 1;

    /** Source of randomness for rolled HP. Seedable via the constructor for determinism. */
    private final RandomGenerator rng;

    HpCalculator(RandomGenerator rng) {
        this.rng = rng;
    }

    /**
     * Per-level HP gain for the chosen mode: the hit-die value (fixed average, or a server-side
     * roll in {@link HpMode#ROLL}) plus the CON modifier, floored at {@value #MIN_HP_PER_LEVEL}.
     * A {@code null} mode is treated as {@link HpMode#AVERAGE}.
     */
    int gain(int hitDie, int conMod, HpMode mode) {
        int dieValue = (mode == HpMode.ROLL)
                ? rollHitDie(hitDie)
                : ClassProgression.averageHitDieValue(hitDie);
        return Math.max(MIN_HP_PER_LEVEL, dieValue + conMod);
    }

    /** Average-mode HP gain — used by the preview (a roll isn't known until commit). */
    int average(int hitDie, int conMod) {
        return gain(hitDie, conMod, HpMode.AVERAGE);
    }

    /** Roll a single hit die on the server: a uniform result in {@code [1, hitDie]}. */
    private int rollHitDie(int hitDie) {
        return rng.nextInt(hitDie) + 1;
    }
}
