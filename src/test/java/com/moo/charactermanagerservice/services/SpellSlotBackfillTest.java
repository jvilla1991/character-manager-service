package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.services.SpellSlotBackfill.Change;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The per-row decision of the V58 backfill, without a database: what one
 * character's {@code spell_slots} and {@code stat_adjustments} become when the
 * slot maximum moves to the server.
 */
class SpellSlotBackfillTest {

    private static final String TAUNKA = "{\"ac\":[{\"value\":1,\"label\":\"Taunka\"}]}";

    /** Run the backfill on one row and return what the row holds afterwards. */
    private static Change after(String clazz, Integer level, String slots, String adjustments) {
        return SpellSlotBackfill.plan(clazz, level, slots, adjustments)
                .orElse(new Change(slots, adjustments));
    }

    private static void assertUnchanged(String clazz, Integer level, String slots, String adjustments) {
        assertThat(SpellSlotBackfill.plan(clazz, level, slots, adjustments)).isEmpty();
    }

    /** The backfill run again on its own output must find nothing left to do. */
    private static void assertSettled(String clazz, Integer level, Change change) {
        assertThat(SpellSlotBackfill.plan(clazz, level, change.spellSlots(), change.statAdjustments()))
                .as("second run").isEmpty();
    }

    // --- already right: no write ---

    @Test
    void aCasterWhoseSlotsMatchTheTable_isNotTouched() {
        assertUnchanged("Wizard", 3, "{\"1\":{\"max\":4,\"used\":1},\"2\":{\"max\":2,\"used\":0}}", null);
        assertUnchanged("Warlock", 5, "{\"3\":{\"max\":2,\"used\":2}}", TAUNKA);
    }

    @Test
    void spacingAndKeyOrderAreNotAReasonToRewrite() {
        assertUnchanged("Wizard", 3,
                "{ \"2\": {\"used\": 0, \"max\": 2}, \"1\": {\"used\": 1, \"max\": 4} }", null);
    }

    @Test
    void aCharacterWithoutSlots_isNotTouched() {
        assertUnchanged("Fighter", 5, null, null);
        assertUnchanged("Fighter", 5, "{}", null);
        assertUnchanged("Fighter", 5, "", TAUNKA);
        assertUnchanged("Rogue", 9, "  ", "  ");
    }

    // --- stored above the table: carried over as a bonus line ---

    @Test
    void slotsAboveTheTable_areCarriedOverAsABonusLine() {
        // A level 3 wizard has 4 / 2; the DM had typed a third 2nd-level slot.
        Change change = after("Wizard", 3, "{\"1\":{\"max\":4,\"used\":1},\"2\":{\"max\":3,\"used\":3}}", null);

        assertThat(change.statAdjustments()).isEqualTo("{\"slot2\":[{\"value\":1,\"label\":\"Carried over\"}]}");
        assertThat(change.spellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":1},\"2\":{\"max\":3,\"used\":3}}");
        assertSettled("Wizard", 3, change);
    }

    @Test
    void aSpellLevelTheTableDoesNotHave_isCarriedOverWhole() {
        Change change = after("Wizard", 1, "{\"1\":{\"max\":2,\"used\":0},\"3\":{\"max\":1,\"used\":1}}", TAUNKA);

        assertThat(change.statAdjustments()).isEqualTo("{\"ac\":[{\"value\":1,\"label\":\"Taunka\"}],"
                + "\"slot3\":[{\"value\":1,\"label\":\"Carried over\"}]}");
        assertThat(change.spellSlots()).isEqualTo("{\"1\":{\"max\":2,\"used\":0},\"3\":{\"max\":1,\"used\":1}}");
        assertSettled("Wizard", 1, change);
    }

    @Test
    void aNonCasterWithHandTypedSlots_keepsThemAsBonusLines() {
        Change change = after("Fighter", 7, "{\"1\":{\"max\":4,\"used\":2},\"2\":{\"max\":2,\"used\":0}}", null);

        assertThat(change.statAdjustments()).isEqualTo("{\"slot1\":[{\"value\":4,\"label\":\"Carried over\"}],"
                + "\"slot2\":[{\"value\":2,\"label\":\"Carried over\"}]}");
        assertThat(change.spellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":2},\"2\":{\"max\":2,\"used\":0}}");
        assertSettled("Fighter", 7, change);
    }

    @Test
    void aHalfCasterWithHandTypedSlots_getsTheTable_andKeepsOnlyTheExtra() {
        // Level 5 paladin: the table says 4 / 2. Typed in: 5 first-level (one extra), one
        // second-level (fewer than the table), one third-level (not on the table yet).
        Change change = after("Paladin", 5,
                "{\"1\":{\"max\":5,\"used\":5},\"2\":{\"max\":1,\"used\":1},\"3\":{\"max\":1,\"used\":0}}", null);

        assertThat(change.statAdjustments()).isEqualTo("{\"slot1\":[{\"value\":1,\"label\":\"Carried over\"}],"
                + "\"slot3\":[{\"value\":1,\"label\":\"Carried over\"}]}");
        assertThat(change.spellSlots()).isEqualTo(
                "{\"1\":{\"max\":5,\"used\":5},\"2\":{\"max\":2,\"used\":1},\"3\":{\"max\":1,\"used\":0}}");
        assertSettled("Paladin", 5, change);
    }

    @Test
    void theCarriedOverAmountIsCappedAtTheLargestLineValue() {
        Change change = after("Fighter", 1, "{\"1\":{\"max\":500,\"used\":300}}", null);

        assertThat(change.statAdjustments()).isEqualTo("{\"slot1\":[{\"value\":99,\"label\":\"Carried over\"}]}");
        assertThat(change.spellSlots()).isEqualTo("{\"1\":{\"max\":99,\"used\":99}}");
        assertSettled("Fighter", 1, change);
    }

    @Test
    void whatWasCarriedOverPassesTheRulesOfAnOrdinarySave() {
        Change change = after("Fighter", 7, "{\"1\":{\"max\":4,\"used\":2},\"9\":{\"max\":500,\"used\":0}}", TAUNKA);

        assertThat(StatAdjustmentRules.normalizeJson(change.statAdjustments()))
                .isEqualTo(change.statAdjustments());
    }

    // --- stored below the table, or not at all: the table's number, no line ---

    @Test
    void slotsBelowTheTable_areRaisedToIt_withoutALine() {
        Change change = after("Wizard", 5, "{\"1\":{\"max\":2,\"used\":2},\"2\":{\"max\":3,\"used\":1}}", TAUNKA);

        assertThat(change.statAdjustments()).isEqualTo(TAUNKA);
        assertThat(change.spellSlots()).isEqualTo(
                "{\"1\":{\"max\":4,\"used\":2},\"2\":{\"max\":3,\"used\":1},\"3\":{\"max\":2,\"used\":0}}");
        assertSettled("Wizard", 5, change);
    }

    @Test
    void aHalfCasterWithNothingStored_getsItsFirstSlots() {
        for (String stored : new String[] {null, "", "{}"}) {
            Change change = after("Ranger", 5, stored, null);

            assertThat(change.statAdjustments()).isNull();
            assertThat(change.spellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":0}}");
            assertSettled("Ranger", 5, change);
        }
    }

    @Test
    void aMissingLevelCountsAsLevelOne_andClassNamesAreNotCaseSensitive() {
        assertThat(after(" paladin ", null, null, null).spellSlots()).isEqualTo("{\"1\":{\"max\":2,\"used\":0}}");
    }

    // --- used is kept, within the new maximum ---

    @Test
    void usedIsClampedToTheNewMaximum() {
        Change change = after("Wizard", 1, "{\"1\":{\"max\":2,\"used\":7}}", null);

        assertThat(change.spellSlots()).isEqualTo("{\"1\":{\"max\":2,\"used\":2}}");
        assertSettled("Wizard", 1, change);
    }

    @Test
    void entriesThatAreNotSpellLevels_areDropped() {
        Change change = after("Wizard", 1,
                "{\"1\":{\"max\":2,\"used\":1},\"0\":{\"max\":3,\"used\":0},\"12\":{\"max\":1,\"used\":0},\"1b\":5}", null);

        assertThat(change.statAdjustments()).isNull();
        assertThat(change.spellSlots()).isEqualTo("{\"1\":{\"max\":2,\"used\":1}}");
    }

    // --- bonus lines that are already there count ---

    @Test
    void existingBonusLinesAreCounted_soNothingIsCarriedOverTwice() {
        String ring = "{\"slot2\":[{\"value\":1,\"label\":\"Ring\"}]}";

        // 2 from the table + 1 from the Ring = the 3 stored: already right.
        assertUnchanged("Wizard", 3, "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":3,\"used\":0}}", ring);
        // One more than that is carried over next to the Ring.
        Change change = after("Wizard", 3, "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":4,\"used\":0}}", ring);
        assertThat(change.statAdjustments()).isEqualTo(
                "{\"slot2\":[{\"value\":1,\"label\":\"Ring\"},{\"value\":1,\"label\":\"Carried over\"}]}");
        assertSettled("Wizard", 3, change);
    }

    // --- the limits of stat adjustments are respected: a line that will not fit is skipped ---

    @Test
    void aFullListOfLines_takesNoMore() {
        StringBuilder lines = new StringBuilder("{\"slot1\":[");
        for (int i = 0; i < StatAdjustmentRules.LINES_MAX; i++) {
            lines.append(i == 0 ? "" : ",").append("{\"value\":1,\"label\":\"Charm ").append(i).append("\"}");
        }
        String full = lines.append("]}").toString(); // 12 lines of +1 on a Fighter: 12 slots

        Change change = after("Fighter", 1, "{\"1\":{\"max\":20,\"used\":15}}", full);

        assertThat(change.statAdjustments()).isEqualTo(full);
        assertThat(change.spellSlots()).isEqualTo("{\"1\":{\"max\":12,\"used\":12}}");
        assertSettled("Fighter", 1, change);
    }

    @Test
    void aFullSetOfAdjustedNumbers_takesNoNewOne() {
        StringBuilder stats = new StringBuilder("{");
        for (int i = 0; i < StatAdjustmentRules.STATS_MAX; i++) {
            stats.append(i == 0 ? "" : ",").append("\"stat").append(i).append("\":[{\"value\":1,\"label\":\"x\"}]");
        }
        String full = stats.append("}").toString();

        Change change = after("Fighter", 1, "{\"1\":{\"max\":2,\"used\":1}}", full);

        // No room for a "slot1" entry: the line is skipped and the Fighter is back to no slots.
        assertThat(change.statAdjustments()).isEqualTo(full);
        assertThat(change.spellSlots()).isEqualTo("{}");
        assertSettled("Fighter", 1, change);
    }

    // --- unreadable JSON: the row is skipped, the migration goes on ---

    @Test
    void aRowWithUnreadableJson_isSkipped() {
        assertThat(SpellSlotBackfill.plan("Wizard", 3, "not json", null)).isEqualTo(Optional.empty());
        assertThat(SpellSlotBackfill.plan("Wizard", 3, "[1,2]", null)).isEmpty();
        assertThat(SpellSlotBackfill.plan("Wizard", 3, "{\"1\":{\"max\":4", null)).isEmpty();
        assertThat(SpellSlotBackfill.plan("Paladin", 3, null, "not json")).isEmpty();
        assertThat(SpellSlotBackfill.plan("Paladin", 3, "{\"1\":{\"max\":9,\"used\":0}}", "\"text\"")).isEmpty();
    }

    @Test
    void oddButReadableValues_doNotThrow() {
        // Entries of the wrong shape are ignored; a "slot" key that is not a list takes no line.
        Change change = after("Wizard", 1,
                "{\"1\":\"four\",\"2\":{\"max\":\"many\",\"used\":null},\"3\":{\"max\":1}}",
                "{\"slot3\":\"oops\",\"ac\":5}");

        assertThat(change.statAdjustments()).isEqualTo("{\"slot3\":\"oops\",\"ac\":5}");
        assertThat(change.spellSlots()).isEqualTo("{\"1\":{\"max\":2,\"used\":0}}");
        assertSettled("Wizard", 1, change);
    }

    @Test
    void anUnknownOrMissingClass_isTreatedAsHavingNoTable() {
        assertUnchanged(null, 5, null, null);
        Change change = after("Artificer", 5, "{\"1\":{\"max\":4,\"used\":1}}", null);
        assertThat(change.statAdjustments()).isEqualTo("{\"slot1\":[{\"value\":4,\"label\":\"Carried over\"}]}");
        assertThat(change.spellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":1}}");
    }
}
