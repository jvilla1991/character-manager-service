package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.progression.ClassResources.Resource;
import com.moo.charactermanagerservice.progression.MysticArtsSpellcasting;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Mystic Focus, the Monk level 6 feature of the Warrior of the Mystic Arts (Arcana Unleashed):
 * a spell slot becomes Focus Points, or Focus Points become a spell slot. Pure rules on one
 * character; {@link PCService#mysticFocus} decides who may call it, persists and logs.
 *
 * <ul>
 *   <li>{@link #SLOT_TO_FOCUS}: spend one unspent slot of the level, regain as many expended
 *       Focus Points as the slot's level (never more than are expended).</li>
 *   <li>{@link #FOCUS_TO_SLOT}: spend unexpended Focus Points to get one expended slot of the
 *       level back. Cost and the Monk level needed, by slot level: 1 costs 2 from Monk 6, 2 costs 3
 *       from Monk 7, 3 costs 5 from Monk 13, 4 costs 6 from Monk 19.</li>
 * </ul>
 *
 * <p>The slots are {@link SpellSlotState}'s and the Focus Points the {@code focusPoints} class
 * resource ({@link ClassResourceState}, stored as the amount USED), so both keep their own
 * maximums and clamping. When the rule lets the player do either (any time; at the end of a
 * Short Rest or with Uncanny Metabolism) is the table's business: nothing here reads a rest clock.
 *
 * <p>400 for a request that can never work on this character (not this subclass, below Monk 6,
 * an unknown action, a slot level outside 1-4, a slot level the Monk level cannot recover yet);
 * 409 for one that cannot work right now (no slot to spend or to recover, no expended or not
 * enough unexpended Focus Points). Nothing is changed when it throws.
 */
final class MysticFocusRules {

    private MysticFocusRules() {}

    static final String SLOT_TO_FOCUS = "SLOT_TO_FOCUS";
    static final String FOCUS_TO_SLOT = "FOCUS_TO_SLOT";

    /** The class resource key of a Monk's Focus Points. */
    static final String FOCUS_POINTS = "focusPoints";

    /**
     * Apply one conversion to the character. Returns what happened, for the activity log:
     * {@code "turned a level 2 spell slot into 2 Focus Points (1 of 2 slots left, 7 of 8 Focus Points)"}.
     */
    static String apply(PC pc, String action, Integer slotLevel) {
        int monkLevel = pc.getLevel() == null ? 1 : pc.getLevel();
        if (!MysticArtsSpellcasting.applies(pc.getClazz(), pc.getSubclass())
                || monkLevel < MysticArtsSpellcasting.MYSTIC_FOCUS_LEVEL) {
            throw bad("Mystic Focus needs a " + MysticArtsSpellcasting.SUBCLASS + " of Monk level "
                    + MysticArtsSpellcasting.MYSTIC_FOCUS_LEVEL + " or higher");
        }
        String what = action == null ? "" : action.trim();
        if (!SLOT_TO_FOCUS.equals(what) && !FOCUS_TO_SLOT.equals(what)) {
            throw bad("action must be " + SLOT_TO_FOCUS + " or " + FOCUS_TO_SLOT);
        }
        if (!MysticArtsSpellcasting.isFocusSlotLevel(slotLevel)) {
            throw bad("slotLevel must be a spell level from 1 to " + MysticArtsSpellcasting.MAX_SLOT_LEVEL);
        }
        Resource focus = ClassResourceState.resourcesOf(pc).stream()
                .filter(r -> FOCUS_POINTS.equals(r.key()))
                .findFirst()
                .orElseThrow(() -> bad("This character has no Focus Points"));
        int expended = ClassResourceState.usedOf(pc, focus);

        if (SLOT_TO_FOCUS.equals(what)) {
            SpellSlotState.Slot slot = SpellSlotState.current(pc).get(slotLevel);
            if (slot == null || slot.used() >= slot.max()) {
                throw conflict("No level " + slotLevel + " slot available");
            }
            if (expended <= 0) {
                throw conflict("No expended Focus Points to regain");
            }
            int regained = Math.min(MysticArtsSpellcasting.focusRegainedFor(slotLevel), expended);
            SpellSlotState.spend(pc, slotLevel);
            ClassResourceState.setUsed(pc, focus, expended - regained);
            return "turned a level " + slotLevel + " spell slot into " + points(regained)
                    + " (" + standing(pc, slotLevel, focus) + ")";
        }

        int minLevel = MysticArtsSpellcasting.minLevelToRecover(slotLevel);
        if (monkLevel < minLevel) {
            throw bad("Recovering a level " + slotLevel + " spell slot needs Monk level " + minLevel);
        }
        SpellSlotState.Slot slot = SpellSlotState.current(pc).get(slotLevel);
        if (slot == null || slot.used() <= 0) {
            throw conflict("No spent level " + slotLevel + " slot to restore");
        }
        int cost = MysticArtsSpellcasting.focusCostFor(slotLevel);
        if (focus.max() - expended < cost) {
            throw conflict("Recovering a level " + slotLevel + " spell slot costs " + points(cost));
        }
        SpellSlotState.restore(pc, slotLevel);
        ClassResourceState.setUsed(pc, focus, expended + cost);
        return "turned " + points(cost) + " into a level " + slotLevel + " spell slot ("
                + standing(pc, slotLevel, focus) + ")";
    }

    /** "1 of 2 slots left, 7 of 8 Focus Points" — where both stand after the conversion. */
    private static String standing(PC pc, int slotLevel, Resource focus) {
        SpellSlotState.Slot slot = SpellSlotState.current(pc).get(slotLevel);
        int left = focus.max() - ClassResourceState.usedOf(pc, focus);
        return (slot.max() - slot.used()) + " of " + slot.max() + " slots left, "
                + left + " of " + focus.max() + " Focus Points";
    }

    private static String points(int n) {
        return n + (n == 1 ? " Focus Point" : " Focus Points");
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
