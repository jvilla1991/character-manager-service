package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.models.PC;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The spell-slot maximum of a character whose SUBCLASS brings the slots: a Warrior of the Mystic
 * Arts monk. Still server-owned: its own table plus the DM's bonus lines.
 */
class SpellSlotStateSubclassTest {

    private static final String MYSTIC_ARTS = "Warrior of the Mystic Arts";

    private static PC pc(String clazz, String subclass, int level, String slots, String adjustments) {
        PC pc = new PC();
        pc.setId(7L);
        pc.setName("Shen");
        pc.setClazz(clazz);
        pc.setSubclass(subclass);
        pc.setLevel((short) level);
        pc.setSpellSlots(slots);
        pc.setStatAdjustments(adjustments);
        return pc;
    }

    @Test
    void maxFor_isTheSubclassesTable() {
        assertThat(SpellSlotState.maxFor(pc("Monk", MYSTIC_ARTS, 2, null, null))).isEmpty();
        assertThat(SpellSlotState.maxFor(pc("Monk", MYSTIC_ARTS, 3, null, null))).isEqualTo(Map.of(1, 2));
        assertThat(SpellSlotState.maxFor(pc("Monk", MYSTIC_ARTS, 7, null, null))).isEqualTo(Map.of(1, 4, 2, 2));
        assertThat(SpellSlotState.maxFor(pc("Monk", MYSTIC_ARTS, 20, null, null)))
                .isEqualTo(Map.of(1, 4, 2, 3, 3, 3, 4, 1));
    }

    @Test
    void maxFor_addsTheDmsBonusLines_onTopOfIt() {
        String lines = "{\"slot1\":[{\"value\":1,\"label\":\"Boon\"}],\"slot3\":[{\"value\":1,\"label\":\"Pearl of Power\"}]}";

        assertThat(SpellSlotState.maxFor(pc("Monk", MYSTIC_ARTS, 7, null, lines)))
                .isEqualTo(Map.of(1, 5, 2, 2, 3, 1));
    }

    @Test
    void maxFor_everyOtherCharacter_isWhatItWasWithoutASubclass() {
        String[][] characters = {
                {"Monk", null}, {"Monk", "Warrior of the Open Hand"}, {"Fighter", "Eldritch Knight"},
                {"Rogue", "Arcane Trickster"}, {"Paladin", "Oath of Devotion"}, {"Wizard", "Evoker"},
                {"Warlock", "Vestige Patron"}, {"Fighter", MYSTIC_ARTS}};
        for (String[] c : characters) {
            for (int level = 1; level <= 20; level++) {
                assertThat(SpellSlotState.maxFor(pc(c[0], c[1], level, null, null))).as(c[0] + "/" + c[1] + " " + level)
                        .isEqualTo(SpellSlotState.maxFor(c[0], level, null));
            }
        }
        // The three-argument form (the V58 backfill's) never reads a subclass.
        assertThat(SpellSlotState.maxFor("Monk", 10, null)).isEmpty();
        assertThat(SpellSlotState.maxFor("Monk", MYSTIC_ARTS, 10, null)).isEqualTo(Map.of(1, 4, 2, 3));
    }

    @Test
    void normalize_givesTheMonkItsSlots_andKeepsWhatIsSpent() {
        PC existing = pc("Monk", MYSTIC_ARTS, 7, "{\"1\":{\"max\":4,\"used\":3},\"2\":{\"max\":2,\"used\":1}}", null);
        PC incoming = pc("Monk", MYSTIC_ARTS, 7, "{\"1\":{\"max\":9,\"used\":0}}", null);

        // A campaign player's body: the max is never the body's, and neither is the used count.
        assertThat(SpellSlotState.normalize(incoming, existing, false))
                .isEqualTo("{\"1\":{\"max\":4,\"used\":3},\"2\":{\"max\":2,\"used\":1}}");
        // The DM's body may move the used count; the max still comes from the table.
        assertThat(SpellSlotState.normalize(incoming, existing, true))
                .isEqualTo("{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":1}}");
    }

    @Test
    void spendAndRestore_workOnItsSlots() {
        PC monk = pc("Monk", MYSTIC_ARTS, 7, null, null);

        assertThat(SpellSlotState.spend(monk, 2)).isTrue();
        assertThat(SpellSlotState.spend(monk, 2)).isTrue();
        assertThat(SpellSlotState.spend(monk, 2)).isFalse(); // only two
        assertThat(SpellSlotState.spend(monk, 3)).isFalse(); // none of that level
        assertThat(monk.getSpellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":2}}");
        assertThat(SpellSlotState.restoreAll(monk)).isEqualTo(2);
        assertThat(SpellSlotState.describe(monk, 2)).isEqualTo("2 of 2 left");
    }

    @Test
    void theBackfillDecision_isUnchanged_forAMonkRow() {
        // V58 ran before the subclass existed and reads no subclass: a Monk row is left alone.
        assertThat(SpellSlotBackfill.plan("Monk", 10, null, null)).isEmpty();
    }
}
