package com.moo.charactermanagerservice.progression;

/**
 * 2024 PHB cumulative XP thresholds, indexed by character level (1–20) — the
 * server-side twin of the frontend's {@code models/xp-thresholds.ts}. A
 * character at level L has earned a level-up once their total XP reaches the
 * threshold for L+1. Used to gate the level-up endpoint; XP itself accumulates
 * only through DM awards in Session Mode. (The 2024 table matches 2014.)
 */
public final class XpThresholds {

    public static final int MAX_LEVEL = 20;

    private static final int[] THRESHOLDS = {
            0,        // L1
            300,      // L2
            900,      // L3
            2_700,    // L4
            6_500,    // L5
            14_000,   // L6
            23_000,   // L7
            34_000,   // L8
            48_000,   // L9
            64_000,   // L10
            85_000,   // L11
            100_000,  // L12
            120_000,  // L13
            140_000,  // L14
            165_000,  // L15
            195_000,  // L16
            225_000,  // L17
            265_000,  // L18
            305_000,  // L19
            355_000,  // L20
    };

    private XpThresholds() {}

    /** Whether a character at {@code level} with {@code xp} total XP has earned the next level. */
    public static boolean isReadyToLevel(int level, int xp) {
        if (level < 1 || level >= MAX_LEVEL) return false;
        return xp >= THRESHOLDS[level]; // threshold for level+1 lives at index `level`
    }
}
