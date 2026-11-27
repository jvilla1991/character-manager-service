package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.models.PC;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mystic Focus, both ways, row by row: what is spent and gained, and every refusal (400 for a
 * request that can never work on this character, 409 for one that cannot work right now).
 */
class MysticFocusRulesTest {

    private static final String MYSTIC_ARTS = "Warrior of the Mystic Arts";
    private static final String SLOT_TO_FOCUS = "SLOT_TO_FOCUS";
    private static final String FOCUS_TO_SLOT = "FOCUS_TO_SLOT";

    /**
     * A Mystic Arts monk whose slots of every spell level it has are spent {@code slotsUsed}
     * times (clamped to what it has) and who has expended {@code focusUsed} Focus Points.
     */
    private static PC monk(int level, int slotsUsed, int focusUsed) {
        PC pc = new PC();
        pc.setId(1L);
        pc.setName("Shen");
        pc.setClazz("Monk");
        pc.setSubclass(MYSTIC_ARTS);
        pc.setLevel((short) level);
        StringBuilder slots = new StringBuilder("{");
        for (int spellLevel = 1; spellLevel <= 4; spellLevel++) {
            if (spellLevel > 1) slots.append(',');
            slots.append('"').append(spellLevel).append("\":{\"max\":9,\"used\":").append(slotsUsed).append('}');
        }
        pc.setSpellSlots(slots.append('}').toString());
        pc.setSpellSlots(SpellSlotState.rebuild(pc)); // the table's maximums, used clamped to them
        pc.setClassResources(focusUsed > 0 ? "{\"focusPoints\":" + focusUsed + "}" : null);
        return pc;
    }

    private static void assertStatus(Runnable call, int status, String reason) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode().value()).isEqualTo(status);
                    assertThat(rse.getReason()).isEqualTo(reason);
                });
    }

    private static int slotsUsed(PC pc, int spellLevel) {
        return SpellSlotState.current(pc).get(spellLevel).used();
    }

    private static int focusUsed(PC pc) {
        return ClassResourceState.resourcesOf(pc).stream()
                .filter(r -> "focusPoints".equals(r.key()))
                .mapToInt(r -> ClassResourceState.usedOf(pc, r))
                .findFirst().orElseThrow();
    }

    // ── Spell slot → Focus Points ────────────────────────────────────────────────

    @Test
    void slotToFocus_spendsTheSlot_andRegainsAsManyPointsAsItsLevel_forEverySlotLevel() {
        for (int slotLevel = 1; slotLevel <= 4; slotLevel++) {
            PC monk = monk(20, 0, 10);

            MysticFocusRules.apply(monk, SLOT_TO_FOCUS, slotLevel);

            assertThat(slotsUsed(monk, slotLevel)).as("level " + slotLevel + " slots used").isEqualTo(1);
            assertThat(focusUsed(monk)).as("Focus Points used after a level " + slotLevel + " slot")
                    .isEqualTo(10 - slotLevel);
            // The other spell levels are untouched.
            for (int other = 1; other <= 4; other++) {
                if (other != slotLevel) assertThat(slotsUsed(monk, other)).isZero();
            }
        }
    }

    @Test
    void slotToFocus_neverRegainsMoreThanIsExpended() {
        PC monk = monk(13, 0, 1);

        String done = MysticFocusRules.apply(monk, SLOT_TO_FOCUS, 3);

        assertThat(focusUsed(monk)).isZero();
        assertThat(monk.getClassResources()).isNull(); // nothing used is not stored
        assertThat(slotsUsed(monk, 3)).isEqualTo(1);
        assertThat(done).isEqualTo(
                "turned a level 3 spell slot into 1 Focus Point (1 of 2 slots left, 13 of 13 Focus Points)");
    }

    @Test
    void slotToFocus_describesWhatHappened_forTheLog() {
        PC monk = monk(8, 1, 5);

        assertThat(MysticFocusRules.apply(monk, SLOT_TO_FOCUS, 2)).isEqualTo(
                "turned a level 2 spell slot into 2 Focus Points (0 of 2 slots left, 5 of 8 Focus Points)");
    }

    @Test
    void slotToFocus_409_whenNoSlotOfThatLevelIsUnspent_orTheMonkHasNoneOfThatLevel() {
        PC spent = monk(7, 9, 4);
        assertStatus(() -> MysticFocusRules.apply(spent, SLOT_TO_FOCUS, 1), 409, "No level 1 slot available");
        assertStatus(() -> MysticFocusRules.apply(spent, SLOT_TO_FOCUS, 2), 409, "No level 2 slot available");
        // A Monk 6 has no level 2 slots at all; a Monk 18 no level 4 slot.
        assertStatus(() -> MysticFocusRules.apply(monk(6, 0, 4), SLOT_TO_FOCUS, 2), 409, "No level 2 slot available");
        assertStatus(() -> MysticFocusRules.apply(monk(18, 0, 4), SLOT_TO_FOCUS, 4), 409, "No level 4 slot available");
        assertThat(focusUsed(spent)).isEqualTo(4);
    }

    @Test
    void slotToFocus_409_whenNoFocusPointIsExpended() {
        PC monk = monk(7, 0, 0);

        assertStatus(() -> MysticFocusRules.apply(monk, SLOT_TO_FOCUS, 1), 409, "No expended Focus Points to regain");

        assertThat(slotsUsed(monk, 1)).isZero(); // the slot is not wasted
    }

    // ── Focus Points → spell slot ────────────────────────────────────────────────

    @Test
    void focusToSlot_spendsTheCost_andRestoresOneSlot_forEveryRowOfTheTable() {
        int[][] table = {{1, 2, 6}, {2, 3, 7}, {3, 5, 13}, {4, 6, 19}}; // slot level, cost, minimum Monk level
        for (int[] row : table) {
            int slotLevel = row[0];
            int cost = row[1];
            int minLevel = row[2];
            PC monk = monk(minLevel, 1, 0);

            MysticFocusRules.apply(monk, FOCUS_TO_SLOT, slotLevel);

            assertThat(focusUsed(monk)).as("cost of a level " + slotLevel + " slot").isEqualTo(cost);
            assertThat(slotsUsed(monk, slotLevel)).as("level " + slotLevel + " slots used").isZero();
        }
    }

    @Test
    void focusToSlot_400_belowTheMonkLevelTheSlotLevelNeeds_forEveryRow() {
        int[][] table = {{2, 7}, {3, 13}, {4, 19}}; // slot level, minimum Monk level (level 1 needs 6: the feature itself)
        for (int[] row : table) {
            PC monk = monk(row[1] - 1, 1, 0);
            assertStatus(() -> MysticFocusRules.apply(monk, FOCUS_TO_SLOT, row[0]), 400,
                    "Recovering a level " + row[0] + " spell slot needs Monk level " + row[1]);
            assertThat(focusUsed(monk)).isZero();
        }
    }

    @Test
    void focusToSlot_theLevelGateHoldsEvenWhenTheDmGrantedSuchASlot() {
        // A bonus line gives a Monk 6 a level 2 slot; Mystic Focus still cannot recover it before Monk 7.
        PC monk = monk(6, 0, 0);
        monk.setStatAdjustments("{\"slot2\":[{\"value\":1,\"label\":\"Boon\"}]}");
        monk.setSpellSlots("{\"1\":{\"max\":3,\"used\":0},\"2\":{\"max\":1,\"used\":1}}");

        assertStatus(() -> MysticFocusRules.apply(monk, FOCUS_TO_SLOT, 2), 400,
                "Recovering a level 2 spell slot needs Monk level 7");
        // The other way is open: the slot exists, so it can be spent (once it is unspent).
        monk.setSpellSlots("{\"1\":{\"max\":3,\"used\":0},\"2\":{\"max\":1,\"used\":0}}");
        monk.setClassResources("{\"focusPoints\":3}");
        MysticFocusRules.apply(monk, SLOT_TO_FOCUS, 2);
        assertThat(focusUsed(monk)).isEqualTo(1);
    }

    @Test
    void focusToSlot_409_whenNoSlotOfThatLevelIsExpended() {
        PC monk = monk(7, 0, 0);

        assertStatus(() -> MysticFocusRules.apply(monk, FOCUS_TO_SLOT, 1), 409, "No spent level 1 slot to restore");
        assertStatus(() -> MysticFocusRules.apply(monk, FOCUS_TO_SLOT, 2), 409, "No spent level 2 slot to restore");

        assertThat(focusUsed(monk)).isZero(); // no points are taken
    }

    @Test
    void focusToSlot_409_whenThereAreTooFewUnexpendedFocusPoints_forEveryRow() {
        int[][] table = {{1, 2, 6}, {2, 3, 7}, {3, 5, 13}, {4, 6, 19}};
        for (int[] row : table) {
            int slotLevel = row[0];
            int cost = row[1];
            int monkLevel = row[2];
            // One point short of the cost: Focus Points are as many as the Monk level.
            PC shortOfIt = monk(monkLevel, 1, monkLevel - cost + 1);
            assertStatus(() -> MysticFocusRules.apply(shortOfIt, FOCUS_TO_SLOT, slotLevel), 409,
                    "Recovering a level " + slotLevel + " spell slot costs " + cost + " Focus Points");
            assertThat(slotsUsed(shortOfIt, slotLevel)).isEqualTo(1);
            // Exactly the cost left is enough, and uses the last points.
            PC justEnough = monk(monkLevel, 1, monkLevel - cost);
            MysticFocusRules.apply(justEnough, FOCUS_TO_SLOT, slotLevel);
            assertThat(focusUsed(justEnough)).isEqualTo(monkLevel);
        }
    }

    @Test
    void focusToSlot_describesWhatHappened_forTheLog() {
        PC monk = monk(8, 2, 1);

        assertThat(MysticFocusRules.apply(monk, FOCUS_TO_SLOT, 2)).isEqualTo(
                "turned 3 Focus Points into a level 2 spell slot (1 of 2 slots left, 4 of 8 Focus Points)");
    }

    // ── Who has the feature, and what a request must carry ───────────────────────

    @Test
    void anyOtherCharacter_isRefused_400() {
        String refused = "Mystic Focus needs a Warrior of the Mystic Arts of Monk level 6 or higher";
        // Below Monk 6.
        for (int level = 1; level <= 5; level++) {
            PC young = monk(level, 0, 1);
            assertStatus(() -> MysticFocusRules.apply(young, SLOT_TO_FOCUS, 1), 400, refused);
            assertStatus(() -> MysticFocusRules.apply(young, FOCUS_TO_SLOT, 1), 400, refused);
        }
        // Another Monk subclass, or none.
        PC openHand = monk(10, 0, 3);
        openHand.setSubclass("Warrior of the Open Hand");
        assertStatus(() -> MysticFocusRules.apply(openHand, SLOT_TO_FOCUS, 1), 400, refused);
        PC plain = monk(10, 0, 3);
        plain.setSubclass(null);
        assertStatus(() -> MysticFocusRules.apply(plain, FOCUS_TO_SLOT, 1), 400, refused);
        // Another class, even one with slots.
        PC wizard = monk(10, 1, 0);
        wizard.setClazz("Wizard");
        assertStatus(() -> MysticFocusRules.apply(wizard, SLOT_TO_FOCUS, 1), 400, refused);
        PC knight = monk(10, 0, 0);
        knight.setClazz("Fighter");
        knight.setSubclass("Eldritch Knight");
        assertStatus(() -> MysticFocusRules.apply(knight, SLOT_TO_FOCUS, 1), 400, refused);
        // Nothing moved on any of them.
        assertThat(openHand.getClassResources()).isEqualTo("{\"focusPoints\":3}");
    }

    @Test
    void anUnknownAction_orSlotLevel_isRefused_400() {
        PC monk = monk(20, 1, 5);
        String before = monk.getSpellSlots();

        for (String action : new String[] {null, "", "slot_to_focus", "RESTORE", "FOCUS"}) {
            assertStatus(() -> MysticFocusRules.apply(monk, action, 1), 400,
                    "action must be SLOT_TO_FOCUS or FOCUS_TO_SLOT");
        }
        for (Integer slotLevel : new Integer[] {null, 0, -1, 5, 9}) {
            assertStatus(() -> MysticFocusRules.apply(monk, SLOT_TO_FOCUS, slotLevel), 400,
                    "slotLevel must be a spell level from 1 to 4");
            assertStatus(() -> MysticFocusRules.apply(monk, FOCUS_TO_SLOT, slotLevel), 400,
                    "slotLevel must be a spell level from 1 to 4");
        }

        assertThat(monk.getSpellSlots()).isEqualTo(before);
        assertThat(focusUsed(monk)).isEqualTo(5);
    }

    @Test
    void theActionMayCarrySurroundingSpaces() {
        PC monk = monk(6, 0, 2);
        MysticFocusRules.apply(monk, "  SLOT_TO_FOCUS ", 1);
        assertThat(focusUsed(monk)).isEqualTo(1);
    }

    @Test
    void otherResourcesOnTheCharacter_areKept() {
        // A feat's counter stays as stored; a stale key the Monk does not have is dropped by the
        // usual cleaning, as on every save.
        PC monk = monk(6, 0, 3);
        monk.setFeatures("[{\"name\":\"Spell Resistant\",\"source\":\"Feat (Level 4)\",\"desc\":\"\"}]");
        monk.setClassResources("{\"focusPoints\":3,\"magicResistant\":1,\"secondWind\":1}");

        MysticFocusRules.apply(monk, SLOT_TO_FOCUS, 1);

        assertThat(monk.getClassResources())
                .contains("\"focusPoints\":2")
                .contains("\"magicResistant\":1")
                .doesNotContain("secondWind");
    }
}
