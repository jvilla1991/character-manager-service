package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.models.PC;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Server-owned spell-slot maximums (class table + DM bonus lines) and the stored "used". */
class SpellSlotStateTest {

    private static PC pc(String clazz, int level, String slots, String adjustments) {
        PC pc = new PC();
        pc.setId(7L);
        pc.setName("Bex");
        pc.setClazz(clazz);
        pc.setLevel((short) level);
        pc.setSpellSlots(slots);
        pc.setStatAdjustments(adjustments);
        return pc;
    }

    private static String line(String stat, int value, String label) {
        return "{\"" + stat + "\":[{\"value\":" + value + ",\"label\":\"" + label + "\"}]}";
    }

    // --- maxFor: class table + bonus lines ---

    @Test
    void maxFor_isTheClassTable_whenThereAreNoBonusLines() {
        assertThat(SpellSlotState.maxFor(pc("Wizard", 5, null, null))).isEqualTo(Map.of(1, 4, 2, 3, 3, 2));
        assertThat(SpellSlotState.maxFor(pc("Paladin", 5, null, null))).isEqualTo(Map.of(1, 4, 2, 2));
        assertThat(SpellSlotState.maxFor(pc("Warlock", 5, null, null))).isEqualTo(Map.of(3, 2));
        assertThat(SpellSlotState.maxFor(pc("Fighter", 5, null, null))).isEmpty();
    }

    @Test
    void maxFor_addsTheBonusLinesOfThatSpellLevel() {
        PC wizard = pc("Wizard", 5, null,
                "{\"slot3\":[{\"value\":1,\"label\":\"Pearl of Power\"},{\"value\":2,\"label\":\"Boon\"}]}");

        assertThat(SpellSlotState.maxFor(wizard)).isEqualTo(Map.of(1, 4, 2, 3, 3, 5));
    }

    @Test
    void maxFor_aBonusLineCanOpenASpellLevelTheTableDoesNotHaveYet() {
        assertThat(SpellSlotState.maxFor(pc("Wizard", 1, null, line("slot4", 1, "Scroll study"))))
                .isEqualTo(Map.of(1, 2, 4, 1));
    }

    @Test
    void maxFor_aClassWithoutSlots_getsASlotFromABonusLine() {
        assertThat(SpellSlotState.maxFor(pc("Fighter", 5, null, line("slot1", 2, "Eldritch Knight"))))
                .isEqualTo(Map.of(1, 2));
    }

    @Test
    void maxFor_aNegativeLineLowersTheMaximum_andLeavesTheLevelOutAtZeroOrLess() {
        assertThat(SpellSlotState.maxFor(pc("Wizard", 5, null, line("slot1", -1, "Cursed"))))
                .isEqualTo(Map.of(1, 3, 2, 3, 3, 2));
        assertThat(SpellSlotState.maxFor(pc("Wizard", 5, null, line("slot3", -2, "Cursed"))))
                .isEqualTo(Map.of(1, 4, 2, 3));
        assertThat(SpellSlotState.maxFor(pc("Wizard", 5, null, line("slot3", -9, "Cursed"))))
                .isEqualTo(Map.of(1, 4, 2, 3));
    }

    @Test
    void maxFor_readsOnlySlot1ToSlot9() {
        String others = "{\"ac\":[{\"value\":1,\"label\":\"Taunka\"}],"
                + "\"slot0\":[{\"value\":1,\"label\":\"x\"}],"
                + "\"slot10\":[{\"value\":1,\"label\":\"x\"}],"
                + "\"Slot1\":[{\"value\":1,\"label\":\"x\"}],"
                + "\"slots\":[{\"value\":1,\"label\":\"x\"}]}";

        assertThat(SpellSlotState.maxFor(pc("Wizard", 1, null, others))).isEqualTo(Map.of(1, 2));
    }

    @Test
    void maxFor_listsSpellLevelsInOrder_andSurvivesUnreadableLines() {
        assertThat(SpellSlotState.maxFor(pc("Fighter", 5, null,
                "{\"slot3\":[{\"value\":1,\"label\":\"a\"}],\"slot1\":[{\"value\":1,\"label\":\"b\"}]}")).keySet())
                .containsExactly(1, 3);
        assertThat(SpellSlotState.maxFor(pc("Wizard", 1, null, "not json"))).isEqualTo(Map.of(1, 2));
    }

    @Test
    void maxFor_countsAMissingLevelAsLevelOne() {
        PC unleveled = new PC();
        unleveled.setClazz("Wizard");

        assertThat(SpellSlotState.maxFor(unleveled)).isEqualTo(Map.of(1, 2));
    }

    // --- normalize (every whole-character save) ---

    @Test
    void normalize_neverTakesTheBodysMax() {
        PC existing = pc("Wizard", 3, "{\"1\":{\"max\":4,\"used\":1},\"2\":{\"max\":2,\"used\":0}}", null);
        PC incoming = pc("Wizard", 3, "{\"1\":{\"max\":9,\"used\":1},\"2\":{\"max\":9,\"used\":0}}", null);

        assertThat(SpellSlotState.normalize(incoming, existing, true))
                .isEqualTo("{\"1\":{\"max\":4,\"used\":1},\"2\":{\"max\":2,\"used\":0}}");
        assertThat(SpellSlotState.normalize(incoming, existing, false))
                .isEqualTo("{\"1\":{\"max\":4,\"used\":1},\"2\":{\"max\":2,\"used\":0}}");
    }

    @Test
    void normalize_keepsTheStoredUsed_whenTheBodyMayNotSetIt() {
        PC existing = pc("Wizard", 3, "{\"1\":{\"max\":4,\"used\":3},\"2\":{\"max\":2,\"used\":1}}", null);
        PC incoming = pc("Wizard", 3, "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":0}}", null);

        assertThat(SpellSlotState.normalize(incoming, existing, false))
                .isEqualTo("{\"1\":{\"max\":4,\"used\":3},\"2\":{\"max\":2,\"used\":1}}");
    }

    @Test
    void normalize_takesTheBodysUsed_whenTheBodyMaySetIt() {
        PC existing = pc("Wizard", 3, "{\"1\":{\"max\":4,\"used\":3},\"2\":{\"max\":2,\"used\":1}}", null);
        PC incoming = pc("Wizard", 3, "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":2}}", null);

        assertThat(SpellSlotState.normalize(incoming, existing, true))
                .isEqualTo("{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":2}}");
    }

    @Test
    void normalize_keepsTheStoredUsed_forWhateverTheBodyLeavesOut() {
        PC existing = pc("Wizard", 3, "{\"1\":{\"max\":4,\"used\":3},\"2\":{\"max\":2,\"used\":1}}", null);
        String stored = existing.getSpellSlots();

        // No field, an empty object, junk: none of them hands a slot back.
        assertThat(SpellSlotState.normalize(pc("Wizard", 3, null, null), existing, true)).isEqualTo(stored);
        assertThat(SpellSlotState.normalize(pc("Wizard", 3, "{}", null), existing, true)).isEqualTo(stored);
        assertThat(SpellSlotState.normalize(pc("Wizard", 3, "not json", null), existing, true)).isEqualTo(stored);
        // One level sent, the other not; and a level sent without a "used".
        assertThat(SpellSlotState.normalize(pc("Wizard", 3, "{\"2\":{\"used\":0}}", null), existing, true))
                .isEqualTo("{\"1\":{\"max\":4,\"used\":3},\"2\":{\"max\":2,\"used\":0}}");
        assertThat(SpellSlotState.normalize(pc("Wizard", 3, "{\"1\":{\"max\":4}}", null), existing, true))
                .isEqualTo(stored);
    }

    @Test
    void normalize_clampsUsedToZeroAndTheMaximum() {
        PC incoming = pc("Wizard", 3, "{\"1\":{\"max\":4,\"used\":9},\"2\":{\"max\":2,\"used\":-3}}", null);

        assertThat(SpellSlotState.normalize(incoming, null, true))
                .isEqualTo("{\"1\":{\"max\":4,\"used\":4},\"2\":{\"max\":2,\"used\":0}}");
    }

    @Test
    void normalize_aMaximumThatShrank_pullsUsedDownWithIt() {
        // The DM's +2 line is gone from the body's (already settled) adjustments.
        PC existing = pc("Wizard", 1, "{\"1\":{\"max\":4,\"used\":4}}", line("slot1", 2, "Boon"));
        PC incoming = pc("Wizard", 1, null, null);

        assertThat(SpellSlotState.normalize(incoming, existing, true)).isEqualTo("{\"1\":{\"max\":2,\"used\":2}}");
    }

    @Test
    void normalize_dropsSpellLevelsTheCharacterHasNoSlotsFor_andAddsMissingOnes() {
        PC existing = pc("Wizard", 3, "{\"1\":{\"max\":4,\"used\":1},\"5\":{\"max\":2,\"used\":1},\"x\":1}", null);
        PC incoming = pc("Wizard", 3, null, null);

        assertThat(SpellSlotState.normalize(incoming, existing, false))
                .isEqualTo("{\"1\":{\"max\":4,\"used\":1},\"2\":{\"max\":2,\"used\":0}}");
    }

    @Test
    void normalize_usesTheIncomingClassLevelAndLines() {
        PC existing = pc("Wizard", 1, "{\"1\":{\"max\":2,\"used\":2}}", null);
        PC incoming = pc("Wizard", 3, null, line("slot2", 1, "Ring"));

        assertThat(SpellSlotState.normalize(incoming, existing, false))
                .isEqualTo("{\"1\":{\"max\":4,\"used\":2},\"2\":{\"max\":3,\"used\":0}}");
    }

    @Test
    void normalize_givesAHalfCasterItsSlots_evenWhenNothingWasStored() {
        assertThat(SpellSlotState.normalize(pc("Paladin", 1, "{}", null), pc("Paladin", 1, null, null), false))
                .isEqualTo("{\"1\":{\"max\":2,\"used\":0}}");
    }

    @Test
    void normalize_leavesACharacterWithoutSlotsAsStored() {
        // Nothing stored stays nothing stored, whatever the body sends: no churn on a Fighter's row.
        assertThat(SpellSlotState.normalize(pc("Fighter", 5, "{}", null), pc("Fighter", 5, null, null), true))
                .isNull();
        assertThat(SpellSlotState.normalize(pc("Fighter", 5, null, null), pc("Fighter", 5, "{}", null), true))
                .isEqualTo("{}");
        // On create there is nothing stored, so the body's own "no slots" is kept as sent.
        assertThat(SpellSlotState.normalize(pc("Fighter", 1, "{}", null), null, true)).isEqualTo("{}");
        assertThat(SpellSlotState.normalize(pc("Fighter", 1, null, null), null, true)).isNull();
    }

    @Test
    void normalize_emptiesSlotsACharacterNoLongerHas() {
        // Hand-typed slots on a Fighter with no bonus line behind them do not survive a save.
        PC existing = pc("Fighter", 5, "{\"1\":{\"max\":2,\"used\":1}}", null);

        assertThat(SpellSlotState.normalize(pc("Fighter", 5, null, null), existing, true)).isEqualTo("{}");
        assertThat(SpellSlotState.normalize(pc("Fighter", 1, "{\"1\":{\"max\":2,\"used\":0}}", null), null, true))
                .isEqualTo("{}");
    }

    @Test
    void normalize_aBonusLineGivesAClassWithoutSlotsAStoredSlot() {
        PC incoming = pc("Fighter", 5, null, line("slot1", 2, "Eldritch Knight"));

        assertThat(SpellSlotState.normalize(incoming, pc("Fighter", 5, null, null), false))
                .isEqualTo("{\"1\":{\"max\":2,\"used\":0}}");
    }

    // --- rebuild (level-up) ---

    @Test
    void rebuild_carriesUsedOver_andStartsNewLevelsUnspent() {
        PC wizard = pc("Wizard", 3, "{\"1\":{\"max\":3,\"used\":2}}", line("slot1", 1, "Boon"));

        assertThat(SpellSlotState.rebuild(wizard))
                .isEqualTo("{\"1\":{\"max\":5,\"used\":2},\"2\":{\"max\":2,\"used\":0}}");
    }

    @Test
    void rebuild_survivesAnUnreadableColumn() {
        assertThat(SpellSlotState.rebuild(pc("Wizard", 1, "not json", null)))
                .isEqualTo("{\"1\":{\"max\":2,\"used\":0}}");
        assertThat(SpellSlotState.rebuild(pc("Fighter", 5, "{}", null))).isEqualTo("{}");
    }

    // --- reading the column ---

    @Test
    void storedMax_isSortedBySpellLevel_andEmptyForNothingOrJunk() {
        assertThat(SpellSlotState.storedMax("{\"2\":{\"max\":3,\"used\":0},\"1\":{\"max\":4,\"used\":1}}"))
                .containsExactly(Map.entry(1, 4), Map.entry(2, 3));
        assertThat(SpellSlotState.storedMax(null)).isEmpty();
        assertThat(SpellSlotState.storedMax(" ")).isEmpty();
        assertThat(SpellSlotState.storedMax("not json")).isEmpty();
    }

    @Test
    void parse_throwsOnTextThatIsNotAJsonObject_butNotOnNothing() {
        assertThat(SpellSlotState.parse(null)).isEmpty();
        assertThat(SpellSlotState.parse("")).isEmpty();
        assertThat(SpellSlotState.parse("null")).isEmpty();
        assertThatThrownBy(() -> SpellSlotState.parse("not json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpellSlotState.parse("[1,2]")).isInstanceOf(IllegalArgumentException.class);
    }
}
