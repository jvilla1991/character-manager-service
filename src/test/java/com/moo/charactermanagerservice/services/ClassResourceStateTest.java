package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.models.PC;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The stored "used" amounts of a character's class resources. */
class ClassResourceStateTest {

    private static PC pc(String clazz, int level, String resources) {
        PC pc = new PC();
        pc.setId(7L);
        pc.setName("Bex");
        pc.setClazz(clazz);
        pc.setLevel((short) level);
        pc.setClassResources(resources);
        return pc;
    }

    // --- normalize (every whole-character save) ---

    @Test
    void normalize_takesTheBodysAmounts() {
        PC existing = pc("Fighter", 5, "{\"secondWind\":1}");
        PC incoming = pc("Fighter", 5, "{\"secondWind\":2,\"actionSurge\":1}");
        assertThat(ClassResourceState.normalize(incoming, existing))
                .isEqualTo("{\"secondWind\":2,\"actionSurge\":1}");
    }

    @Test
    void normalize_keepsTheStoredAmounts_whenTheBodyOmitsTheField() {
        PC existing = pc("Fighter", 5, "{\"secondWind\":1}");
        PC incoming = pc("Fighter", 5, null);
        assertThat(ClassResourceState.normalize(incoming, existing)).isEqualTo("{\"secondWind\":1}");
    }

    @Test
    void normalize_clampsToZeroAndTheMaximum() {
        PC incoming = pc("Fighter", 5, "{\"secondWind\":9,\"actionSurge\":-3}"); // max 3 and 1
        assertThat(ClassResourceState.normalize(incoming, null)).isEqualTo("{\"secondWind\":3}");
    }

    @Test
    void normalize_dropsResourcesTheCharacterDoesNotHave_andJunk() {
        PC incoming = pc("Fighter", 1, // no Action Surge before level 2
                "{\"secondWind\":1,\"actionSurge\":1,\"rage\":2,\"nonsense\":\"x\",\"layOnHands\":40}");
        assertThat(ClassResourceState.normalize(incoming, null)).isEqualTo("{\"secondWind\":1}");
    }

    @Test
    void normalize_storesNull_whenNothingIsUsed() {
        assertThat(ClassResourceState.normalize(pc("Fighter", 5, "{\"secondWind\":0}"), null)).isNull();
        assertThat(ClassResourceState.normalize(pc("Fighter", 5, "{}"), pc("Fighter", 5, "{\"secondWind\":1}")))
                .isNull(); // an empty map in the body is "everything back", not "field omitted"
        assertThat(ClassResourceState.normalize(pc("Wizard", 5, "{\"secondWind\":1}"), null)).isNull();
        assertThat(ClassResourceState.normalize(pc("Fighter", 5, "not json"), null)).isNull();
    }

    @Test
    void normalize_pullsTheAmountDown_whenTheMaximumShrinks() {
        // A paladin with 40 of 50 Lay On Hands points used is edited down to level 4 (pool 20).
        PC existing = pc("Paladin", 10, "{\"layOnHands\":40}");
        PC incoming = pc("Paladin", 4, null);
        assertThat(ClassResourceState.normalize(incoming, existing)).isEqualTo("{\"layOnHands\":20}");
    }

    @Test
    void normalize_dropsLayOnHands_whenThePaladinBecomesADeathKnight() {
        PC existing = pc("Paladin", 5, "{\"layOnHands\":10,\"channelDivinity\":1}");
        PC incoming = pc("Paladin", 5, null);
        incoming.setSubclass("Oath of the Ebon Blade");
        assertThat(ClassResourceState.normalize(incoming, existing)).isEqualTo("{\"channelDivinity\":1}");
    }

    // --- long rest ---

    @Test
    void restoreAll_givesEverythingBack_andSaysWhat() {
        PC fighter = pc("Fighter", 5, "{\"secondWind\":2,\"actionSurge\":1}");

        assertThat(ClassResourceState.restoreAll(fighter))
                .containsExactly("Second Wind 1 → 3", "Action Surge 0 → 1");
        assertThat(fighter.getClassResources()).isNull();
    }

    @Test
    void restoreAll_saysNothing_whenNothingWasSpent() {
        PC fighter = pc("Fighter", 5, null);
        assertThat(ClassResourceState.restoreAll(fighter)).isEmpty();
        assertThat(fighter.getClassResources()).isNull();
    }

    // --- short rest ---

    @Test
    void shortRest_givesBackOneUse_orEverything_perTheRules() {
        PC fighter = pc("Fighter", 5, "{\"secondWind\":3,\"actionSurge\":1}");

        assertThat(ClassResourceState.shortRest(fighter))
                .containsExactly("Second Wind 0 → 1", "Action Surge 0 → 1");
        assertThat(fighter.getClassResources()).isEqualTo("{\"secondWind\":2}");
    }

    @Test
    void shortRest_leavesLongRestOnlyResourcesAlone() {
        PC paladin = pc("Paladin", 5, "{\"layOnHands\":20,\"channelDivinity\":2}");

        assertThat(ClassResourceState.shortRest(paladin)).containsExactly("Channel Divinity 0 → 1");
        assertThat(paladin.getClassResources()).isEqualTo("{\"layOnHands\":20,\"channelDivinity\":1}");
    }

    @Test
    void shortRest_restoresAllFocusPoints_andBardicInspirationOnlyFromLevelFive() {
        PC monk = pc("Monk", 6, "{\"focusPoints\":5}");
        assertThat(ClassResourceState.shortRest(monk)).containsExactly("Focus Points 1 → 6");
        assertThat(monk.getClassResources()).isNull();

        PC youngBard = pc("Bard", 4, "{\"bardicInspiration\":1}");
        youngBard.setAbilityCha((short) 16);
        assertThat(ClassResourceState.shortRest(youngBard)).isEmpty();
        assertThat(youngBard.getClassResources()).isEqualTo("{\"bardicInspiration\":1}");

        PC bard = pc("Bard", 5, "{\"bardicInspiration\":3}");
        bard.setAbilityCha((short) 16);
        assertThat(ClassResourceState.shortRest(bard)).containsExactly("Bardic Inspiration 0 → 3");
    }

    @Test
    void shortRest_saysNothing_whenNothingWasSpent() {
        PC fighter = pc("Fighter", 5, null);
        assertThat(ClassResourceState.shortRest(fighter)).isEmpty();
        assertThat(fighter.getClassResources()).isNull();
    }
}
