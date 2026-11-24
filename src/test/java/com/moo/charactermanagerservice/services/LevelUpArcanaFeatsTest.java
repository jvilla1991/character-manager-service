package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Arcana Unleashed feats at a level-up: the preview offers only the ones the character
 * qualifies for, reading its feats from where they are stored, and the commit refuses the rest
 * with the missing prerequisite named.
 */
class LevelUpArcanaFeatsTest {

    private final LevelUpService service = new LevelUpService(new ObjectMapper());

    private static final List<String> FAMILIAR_FEATS = List.of(
            "Elemental Familiar", "Otherworldly Familiar", "Soothing Familiar", "Warlike Familiar");

    private static PC pc(String clazz, int level) {
        PC pc = new PC();
        pc.setClazz(clazz);
        pc.setLevel((short) level);
        pc.setAbilityCon((short) 14);
        pc.setHpMax((short) 30);
        pc.setHpCurrent((short) 30);
        return pc;
    }

    private static String feature(String name, String source) {
        return "{\"name\":\"" + name + "\",\"source\":\"" + source + "\",\"desc\":\"\"}";
    }

    private static void assertRefused(Runnable levelUp, String message) {
        assertThatThrownBy(levelUp::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo(message);
                });
    }

    // ── Where the feats are read from ────────────────────────────────────────────

    @Test
    void feats_areReadFromTheOriginFeatAndTheFeaturesList_eachOnce() {
        PC pc = pc("Fighter", 3);
        assertThat(CharacterFeats.namesOn(pc)).isEmpty();
        assertThat(CharacterFeats.namesOn(null)).isEmpty();

        pc.setFeat(" Familiar Friend ");
        pc.setFeatures("[" + feature("Familiar Friend", "Familiar Trainer · Origin Feat") + ","
                + feature("Magic Initiate (Wizard)", "Human (World of Warcraft) · Origin Feat") + ","
                + feature("Second Wind", "Fighter 1") + ","
                + feature("Arcane Warrior", "Fighting Style (Level 2)") + ","
                + feature("Spell Resistant", "Feat (Level 4)") + ","
                + feature("Portal Jumper", "Feat") + ","
                + "{\"source\":\"no name\"},{\"name\":\"  \"}]");
        assertThat(CharacterFeats.namesOn(pc)).containsExactly("Familiar Friend", "Magic Initiate (Wizard)",
                "Second Wind", "Arcane Warrior", "Spell Resistant", "Portal Jumper");

        pc.setFeatures("not json");
        assertThat(CharacterFeats.namesOn(pc)).containsExactly("Familiar Friend");
    }

    // ── Spellcasting or Pact Magic ───────────────────────────────────────────────

    @Test
    void schoolAdepts_areOfferedToCasters_andRefusedToAFighterWithTheReason() {
        assertThat(service.preview(pc("Wizard", 3)).featOptions())
                .contains("Abjuration Adept", "Evocation Adept", "Spell Subterfuge", "Spell Resistant");
        assertThat(service.preview(pc("Paladin", 3)).featOptions()).contains("Necromancy Adept", "Arcane Warrior");
        assertThat(service.preview(pc("Warlock", 3)).featOptions()).contains("Enchantment Adept");

        assertThat(service.preview(pc("Fighter", 3)).featOptions())
                .contains("Spell Resistant", "Arcane Warrior")
                .doesNotContain("Abjuration Adept", "Evocation Adept", "Spell Subterfuge");
        PC fighter = pc("Fighter", 3);
        assertRefused(() -> service.applyLevelUp(fighter, null, null, "Evocation Adept"),
                "'Evocation Adept' requires the Spellcasting or Pact Magic feature");
        assertRefused(() -> service.applyLevelUp(fighter, null, null, "spell subterfuge"),
                "'spell subterfuge' requires the Spellcasting or Pact Magic feature");
        assertThat(fighter.getLevel()).isEqualTo((short) 3);
        assertThat(fighter.getFeatures()).isNull();
    }

    @Test
    void aFeatIsRecordedUnderItsOwnSpelling_likeEveryOtherFeat() {
        PC rogue = pc("Rogue", 3);
        service.applyLevelUp(rogue, null, null, "  spell resistant ");
        assertThat(rogue.getLevel()).isEqualTo((short) 4);
        assertThat(rogue.getFeatures()).contains(feature("Spell Resistant", "Feat (Level 4)"));
        assertThat(CharacterFeats.namesOn(rogue)).contains("Spell Resistant");
        // The +1 to an ability score is the player's to record, as with every half-feat.
        assertThat(rogue.getAbilityCon()).isEqualTo((short) 14);
    }

    // ── The Familiar Friend feat ─────────────────────────────────────────────────

    @Test
    void familiarFeats_needFamiliarFriend_whereverItIsStored() {
        PC without = pc("Wizard", 3);
        assertThat(service.preview(without).featOptions()).doesNotContainAnyElementsOf(FAMILIAR_FEATS);
        assertRefused(() -> service.applyLevelUp(without, null, null, "Soothing Familiar"),
                "'Soothing Familiar' requires the Familiar Friend feat");

        PC fromBackground = pc("Fighter", 3);
        fromBackground.setFeat("Familiar Friend");
        assertThat(service.preview(fromBackground).featOptions()).containsAll(FAMILIAR_FEATS);

        PC fromSpecies = pc("Rogue", 3);
        fromSpecies.setFeat("Alert");
        fromSpecies.setFeatures("[" + feature("Alert", "Criminal · Origin Feat") + ","
                + feature("Familiar Friend", "Human (World of Warcraft) · Origin Feat") + "]");
        assertThat(service.preview(fromSpecies).featOptions()).containsAll(FAMILIAR_FEATS);

        PC fromDm = pc("Barbarian", 3);
        fromDm.setFeatures("[" + feature("familiar friend", "Feat") + "]");
        assertThat(service.preview(fromDm).featOptions()).containsAll(FAMILIAR_FEATS);

        service.applyLevelUp(fromSpecies, null, null, "Elemental Familiar");
        assertThat(fromSpecies.getFeatures()).contains(feature("Elemental Familiar", "Feat (Level 4)"));
    }

    // ── A Magic Initiate feat ────────────────────────────────────────────────────

    @Test
    void magicConnoisseur_needsAMagicInitiateFeat() {
        PC without = pc("Wizard", 3);
        without.setFeat("Skilled");
        assertThat(service.preview(without).featOptions()).doesNotContain("Magic Connoisseur");
        assertRefused(() -> service.applyLevelUp(without, null, null, "Magic Connoisseur"),
                "'Magic Connoisseur' requires a Magic Initiate feat");

        PC sage = pc("Fighter", 3);
        sage.setFeat("Magic Initiate (Wizard)");
        assertThat(service.preview(sage).featOptions()).contains("Magic Connoisseur");

        PC human = pc("Fighter", 3);
        human.setFeat("Tough");
        human.setFeatures("[" + feature("Magic Initiate (Druid)", "Human (World of Warcraft) · Origin Feat") + "]");
        assertThat(service.preview(human).featOptions()).contains("Magic Connoisseur");
    }

    // ── One of each, and a boon per school ───────────────────────────────────────

    @Test
    void aFeatAlreadyTaken_isNotOfferedAndIsRefused() {
        PC wizard = pc("Wizard", 7);
        wizard.setFeatures("[" + feature("Evocation Adept", "Feat (Level 4)") + "]");
        assertThat(service.preview(wizard).featOptions()).doesNotContain("Evocation Adept").contains("Illusion Adept");
        assertRefused(() -> service.applyLevelUp(wizard, null, null, "Evocation Adept"),
                "'Evocation Adept' can be taken only once");
    }

    @Test
    void epicBoons_gateOnLevelSpellcastingAndSchool() {
        PC wizard = pc("Wizard", 18);
        wizard.setFeatures("[" + feature("Boon of Magic School Mastery (Evocation)", "Feat") + "]"); // from the DM
        assertThat(service.preview(wizard).featOptions())
                .contains("Boon of Erupting Spellpower", "Boon of the Iron Mind",
                        "Boon of Magic School Mastery (Abjuration)")
                .doesNotContain("Boon of Magic School Mastery (Evocation)", "Boon of Magic School Mastery");
        assertRefused(() -> service.applyLevelUp(wizard, null, null, "Boon of Magic School Mastery (Evocation)"),
                "'Boon of Magic School Mastery (Evocation)' can be taken only once for each school of magic");

        PC fighter = pc("Fighter", 18);
        assertThat(service.preview(fighter).featOptions())
                .contains("Boon of the Iron Mind")
                .doesNotContain("Boon of Erupting Spellpower", "Boon of Magic School Mastery (Abjuration)");
        assertRefused(() -> service.applyLevelUp(fighter, null, null, "Boon of Erupting Spellpower"),
                "'Boon of Erupting Spellpower' requires the Spellcasting or Pact Magic feature");
        service.applyLevelUp(fighter, null, null, "boon of the iron mind");
        assertThat(fighter.getFeatures()).contains(feature("Boon of the Iron Mind", "Feat (Level 19)"));

        // Below 19 a boon is simply not a feat on offer.
        assertRefused(() -> service.applyLevelUp(pc("Wizard", 15), null, null, "Boon of the Iron Mind"),
                "'Boon of the Iron Mind' is not a selectable feat");
    }

    @Test
    void anUnknownFeat_isStillNotSelectable() {
        assertRefused(() -> service.applyLevelUp(pc("Wizard", 3), null, null, "Arcane Artist"),
                "'Arcane Artist' is not a selectable feat"); // an Origin feat is never taken at a level-up
    }
}
