package com.moo.charactermanagerservice.progression;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Spellcasting of the Warrior of the Mystic Arts (Arcana Unleashed), a Monk subclass that
 * casts Sorcerer spells with Wisdom from Monk level 3: a third-caster table of its own, read by
 * {@link ClassProgression}'s subclass-aware lookups ({@code spellSlotsFor(clazz, subclass, level)}
 * and its siblings). Nothing here applies to any other character.
 *
 * <p>Also the numbers of its level 6 feature, Mystic Focus, which trades spell slots and Focus
 * Points both ways ({@link #focusRegainedFor}, {@link #focusCostFor}, {@link #minLevelToRecover}).
 *
 * <p>The client mirrors the table in {@code utils/mystic-arts.ts}; both sides pin every level.
 */
public final class MysticArtsSpellcasting {

    private MysticArtsSpellcasting() {}

    /** The class the subclass belongs to, as {@link ClassProgression} keys it. */
    public static final String CLASS = "monk";

    /** The subclass, as the catalog names it. */
    public static final String SUBCLASS = "Warrior of the Mystic Arts";

    /** The Monk level its Spellcasting starts at (the subclass level). */
    public static final int SPELLCASTING_LEVEL = 3;

    /** The Monk level Mystic Focus arrives at. */
    public static final int MYSTIC_FOCUS_LEVEL = 6;

    /** The highest spell level the subclass ever has slots for, and Mystic Focus can recover. */
    public static final int MAX_SLOT_LEVEL = 4;

    /** Slots by Monk level (rows, from level 1) and spell level 1-4 (columns); zeros = none. */
    private static final int[][] SLOTS = {
            //  1  2  3  4   <- spell level
            {  0, 0, 0, 0 }, // L1
            {  0, 0, 0, 0 }, // L2
            {  2, 0, 0, 0 }, // L3
            {  3, 0, 0, 0 }, // L4
            {  3, 0, 0, 0 }, // L5
            {  3, 0, 0, 0 }, // L6
            {  4, 2, 0, 0 }, // L7
            {  4, 2, 0, 0 }, // L8
            {  4, 2, 0, 0 }, // L9
            {  4, 3, 0, 0 }, // L10
            {  4, 3, 0, 0 }, // L11
            {  4, 3, 0, 0 }, // L12
            {  4, 3, 2, 0 }, // L13
            {  4, 3, 2, 0 }, // L14
            {  4, 3, 2, 0 }, // L15
            {  4, 3, 3, 0 }, // L16
            {  4, 3, 3, 0 }, // L17
            {  4, 3, 3, 0 }, // L18
            {  4, 3, 3, 1 }, // L19
            {  4, 3, 3, 1 }, // L20
    };

    /** The Prepared Spells column by Monk level, from level 1. */
    private static final int[] PREPARED = {
            0, 0, 3, 4, 4, 4, 5, 6, 6, 7, 8, 8, 9, 10, 10, 11, 11, 11, 12, 13
    };

    /** Focus Points a recovered slot costs, by spell level 1-4. */
    private static final int[] FOCUS_COST = { 2, 3, 5, 6 };

    /** The Monk level needed to recover a slot, by spell level 1-4. */
    private static final int[] RECOVER_MIN_LEVEL = { 6, 7, 13, 19 };

    /** True for a Monk whose subclass is the Warrior of the Mystic Arts (case and spaces ignored). */
    public static boolean applies(String clazz, String subclass) {
        return clazz != null && subclass != null
                && CLASS.equals(clazz.trim().toLowerCase())
                && SUBCLASS.equalsIgnoreCase(subclass.trim());
    }

    /** Spell slots at a Monk level, {@code spellLevel -> slots} in spell-level order; empty below 3. */
    public static Map<Integer, Integer> spellSlots(int level) {
        Map<Integer, Integer> slots = new LinkedHashMap<>();
        if (level < 1) return slots;
        int[] row = SLOTS[Math.min(level, ClassProgression.MAX_LEVEL) - 1];
        for (int i = 0; i < row.length; i++) {
            if (row[i] > 0) slots.put(i + 1, row[i]);
        }
        return slots;
    }

    /** Prepared spells at a Monk level: 3 at level 3, 13 at level 20; 0 below 3. */
    public static int preparedSpells(int level) {
        if (level < 1) return 0;
        return PREPARED[Math.min(level, ClassProgression.MAX_LEVEL) - 1];
    }

    /** Sorcerer cantrips known at a Monk level: two from 3, a third from 10. */
    public static int cantripsKnown(int level) {
        if (level < SPELLCASTING_LEVEL) return 0;
        return level >= 10 ? 3 : 2;
    }

    /** True for a spell level Mystic Focus deals in: 1 to {@value #MAX_SLOT_LEVEL}. */
    public static boolean isFocusSlotLevel(Integer slotLevel) {
        return slotLevel != null && slotLevel >= 1 && slotLevel <= MAX_SLOT_LEVEL;
    }

    /** Focus Points an expended slot of this level gives back, before the cap of what is expended. */
    public static int focusRegainedFor(int slotLevel) {
        return slotLevel;
    }

    /** Focus Points it costs to recover a slot of this level: 2, 3, 5, 6 for levels 1 to 4. */
    public static int focusCostFor(int slotLevel) {
        return FOCUS_COST[slotLevel - 1];
    }

    /** The Monk level needed to recover a slot of this level: 6, 7, 13, 19 for levels 1 to 4. */
    public static int minLevelToRecover(int slotLevel) {
        return RECOVER_MIN_LEVEL[slotLevel - 1];
    }
}
