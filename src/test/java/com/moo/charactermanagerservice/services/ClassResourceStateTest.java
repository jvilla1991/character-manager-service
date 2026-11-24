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

    // --- feat counters (Arcana Unleashed): same map, same rests ---

    private static String feature(String name, String source) {
        return "{\"name\":\"" + name + "\",\"source\":\"" + source + "\",\"desc\":\"\"}";
    }

    @Test
    void featCounters_areReadFromTheOriginFeatAndTheFeaturesList() {
        PC fighter = pc("Fighter", 5, null);
        assertThat(ClassResourceState.resourcesOf(fighter)).extracting(r -> r.key())
                .containsExactly("secondWind", "actionSurge");

        fighter.setFeat("Familiar Friend"); // the background's Origin feat
        fighter.setFeatures("[" + feature("Familiar Friend", "Familiar Trainer · Origin Feat") + ","
                + feature("Portal Jumper", "Human (World of Warcraft) · Origin Feat") + ","
                + feature("Spell Resistant", "Feat (Level 4)") + "]");
        assertThat(ClassResourceState.resourcesOf(fighter)).extracting(r -> r.key() + ":" + r.max())
                .containsExactly("secondWind:3", "actionSurge:1", "findFamiliarFreeCast:1", "helpfulFriend:3",
                        "portalStep:3", "magicResistant:3");
    }

    @Test
    void featCounters_useTheHighestMentalModifier_fromTheStoredScores() {
        PC bard = pc("Bard", 4, null);
        bard.setFeatures("[" + feature("Spell Subterfuge", "Feat (Level 4)") + "]");
        bard.setAbilityInt((short) 12);
        bard.setAbilityWis((short) 9);
        bard.setAbilityCha((short) 18);
        assertThat(ClassResourceState.resourcesOf(bard)).extracting(r -> r.key() + ":" + r.max())
                .containsExactly("bardicInspiration:4", "shroudingSpells:4");
        bard.setAbilityInt((short) 20);
        assertThat(ClassResourceState.resourcesOf(bard)).extracting(r -> r.key() + ":" + r.max())
                .containsExactly("bardicInspiration:4", "shroudingSpells:5");
    }

    @Test
    void normalize_keepsFeatCounters_clampsThem_andDropsTheOnesOfAFeatTheCharacterLacks() {
        PC incoming = pc("Rogue", 5, "{\"portalStep\":9,\"inspiringMagic\":1,\"helpfulFriend\":1}");
        incoming.setFeat("Portal Jumper");
        assertThat(ClassResourceState.normalize(incoming, null)).isEqualTo("{\"portalStep\":3}");

        // A body without the field keeps the stored amounts, read against the body's own feats.
        PC existing = pc("Rogue", 5, "{\"portalStep\":2}");
        existing.setFeat("Portal Jumper");
        PC update = pc("Rogue", 5, null);
        update.setFeat("Portal Jumper");
        assertThat(ClassResourceState.normalize(update, existing)).isEqualTo("{\"portalStep\":2}");
    }

    @Test
    void aLongRest_givesEveryFeatCounterBack() {
        PC wizard = pc("Wizard", 19, "{\"findFamiliarFreeCast\":1,\"helpfulFriend\":4,\"spellOverload\":1,"
                + "\"signatureArcanumEvocation\":1,\"connoisseurLevel2Spell\":1}");
        wizard.setFeat("Familiar Friend");
        wizard.setFeatures("[" + feature("Magic Initiate (Wizard)", "Human (World of Warcraft) · Origin Feat") + ","
                + feature("Magic Connoisseur", "Feat (Level 4)") + ","
                + feature("Boon of Erupting Spellpower", "Feat (Level 19)") + ","
                + feature("Boon of Magic School Mastery (Evocation)", "Feat") + "]");

        assertThat(ClassResourceState.restoreAll(wizard)).containsExactly(
                "Find Familiar (free cast) 0 → 1", "Helpful Friend 2 → 6", "Level 2 spell (free cast) 0 → 1",
                "Spell Overload 0 → 1", "Signature Arcanum (Evocation) 0 → 1");
        assertThat(wizard.getClassResources()).isNull();
    }

    @Test
    void aShortRest_givesBackSpellOverloadOnly() {
        PC wizard = pc("Wizard", 19, "{\"helpfulFriend\":2,\"spellOverload\":1,\"signatureArcanumEvocation\":1}");
        wizard.setFeat("Familiar Friend");
        wizard.setFeatures("[" + feature("Boon of Erupting Spellpower", "Feat (Level 19)") + ","
                + feature("Boon of Magic School Mastery (Evocation)", "Feat") + "]");

        assertThat(ClassResourceState.shortRest(wizard)).containsExactly("Spell Overload 0 → 1");
        assertThat(wizard.getClassResources())
                .isEqualTo("{\"helpfulFriend\":2,\"signatureArcanumEvocation\":1}");
    }
}
