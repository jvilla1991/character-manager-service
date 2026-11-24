package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.dto.SpellGrant;
import com.moo.charactermanagerservice.models.PC;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The spells of the Arcana Unleashed feats at a level-up: what the preview says a character is
 * owed, what a commit saves, and what it refuses.
 */
class LevelUpFeatSpellsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final LevelUpService service = new LevelUpService(JSON);

    private static PC pc(String clazz, int level) {
        PC pc = new PC();
        pc.setClazz(clazz);
        pc.setLevel((short) level);
        pc.setAbilityCon((short) 14);
        pc.setHpMax((short) 30);
        pc.setHpCurrent((short) 30);
        return pc;
    }

    private static Map<String, Object> spell(int level, String name, String school) {
        Map<String, Object> spell = new LinkedHashMap<>();
        spell.put("lvl", level);
        spell.put("name", name);
        spell.put("school", school);
        spell.put("prepared", true);
        return spell;
    }

    /** A spell as the app sends a pick made for a feat: marked with the feat's name. */
    private static Map<String, Object> pick(int level, String name, String school, String feat) {
        Map<String, Object> spell = spell(level, name, school);
        spell.put("grantedBy", feat);
        return spell;
    }

    private static String feature(String name, String source) {
        return "{\"name\":\"" + name + "\",\"source\":\"" + source + "\",\"desc\":\"\"}";
    }

    private static List<Map<String, Object>> spellsOf(PC pc) {
        try {
            return pc.getSpells() == null ? List.of() : JSON.readValue(pc.getSpells(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> saved(PC pc, String name) {
        return spellsOf(pc).stream().filter(s -> name.equals(s.get("name"))).findFirst().orElseThrow();
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

    // ── Spells a feat names: owed until the character has them ───────────────────

    @Test
    void preview_owesTheSpellOfAFeatTheCharacterHas_untilItIsSaved() {
        PC fighter = pc("Fighter", 1);
        fighter.setFeat("Familiar Friend");
        assertThat(service.preview(fighter).alwaysPreparedSpells())
                .containsExactly(new SpellGrant("Find Familiar", "Familiar Friend"));

        fighter.setSpells("[{\"lvl\":1,\"name\":\"Find Familiar\",\"prepared\":true,\"alwaysPrepared\":true,"
                + "\"grantedBy\":\"Familiar Friend\"}]");
        assertThat(service.preview(fighter).alwaysPreparedSpells()).isEmpty();

        // A wizard who learned it the ordinary way is owed the always-prepared marker.
        PC wizard = pc("Wizard", 1);
        wizard.setFeat("Familiar Friend");
        wizard.setSpells("[{\"lvl\":1,\"name\":\"Find Familiar\",\"prepared\":false}]");
        assertThat(service.preview(wizard).alwaysPreparedSpells())
                .containsExactly(new SpellGrant("Find Familiar", "Familiar Friend"));
    }

    @Test
    void preview_aCantripTheCharacterKnows_isNotOwedAgain() {
        PC rogue = pc("Rogue", 1);
        rogue.setFeat("Arcane Artist");
        assertThat(service.preview(rogue).alwaysPreparedSpells())
                .containsExactly(new SpellGrant("Minor Illusion", "Arcane Artist"));
        // The create wizard saves a feat's cantrip with its source and no always-prepared marker.
        rogue.setSpells("[{\"lvl\":0,\"name\":\"Minor Illusion\",\"prepared\":true,\"grantedBy\":\"Arcane Artist\"}]");
        assertThat(service.preview(rogue).alwaysPreparedSpells()).isEmpty();
    }

    @Test
    void preview_listsFeatSpellsAfterTheClassSpells_readingFeatsFromTheFeaturesListToo() {
        PC paladin = pc("Paladin", 4); // -> 5: Faithful Steed
        paladin.setFeat("Alert");
        paladin.setFeatures("[" + feature("Arcane Omens", "Human (World of Warcraft) · Origin Feat") + "]");
        List<SpellGrant> owed = service.preview(paladin).alwaysPreparedSpells();
        assertThat(owed).contains(new SpellGrant("Find Steed", "Faithful Steed"));
        assertThat(owed.get(owed.size() - 1)).isEqualTo(new SpellGrant("Guidance", "Arcane Omens"));
    }

    @Test
    void apply_savesAnOwedFeatSpell_outsideThePicksAndTheSlotCap() {
        PC rogue = pc("Rogue", 1); // no spell slots, no picks
        rogue.setFeat("Familiar Friend");
        service.applyLevelUp(rogue, null, null, null, List.of(spell(1, "Find Familiar", "conjuration")));
        assertThat(saved(rogue, "Find Familiar"))
                .containsEntry("alwaysPrepared", true).containsEntry("grantedBy", "Familiar Friend")
                .containsEntry("prepared", true);
        assertThat(service.preview(rogue).alwaysPreparedSpells()).isEmpty();

        // A request without it adds nothing, and the next level-up offers it again.
        PC late = pc("Rogue", 1);
        late.setFeat("Familiar Friend");
        service.applyLevelUp(late);
        assertThat(late.getSpells()).isNull();
        assertThat(service.preview(late).alwaysPreparedSpells())
                .containsExactly(new SpellGrant("Find Familiar", "Familiar Friend"));
    }

    // ── School Adept tables ──────────────────────────────────────────────────────

    @Test
    void preview_listsTheSpellsOfEachFeatOnOffer_byFeat() {
        LevelUpPreview wizard = service.preview(pc("Wizard", 3)); // -> 4, slot levels 1 and 2
        assertThat(wizard.alwaysPreparedSpells()).isEmpty();
        assertThat(wizard.alwaysPreparedSpellsByFeat().keySet()).containsExactlyInAnyOrder(
                "Abjuration Adept", "Conjuration Adept", "Divination Adept", "Enchantment Adept",
                "Evocation Adept", "Illusion Adept", "Necromancy Adept", "Transmutation Adept");
        assertThat(wizard.alwaysPreparedSpellsByFeat().get("Evocation Adept")).containsExactly(
                new SpellGrant("Chromatic Orb", "Evocation Adept"), new SpellGrant("Shatter", "Evocation Adept"));

        // A paladin at 4 has level 1 slots only; a warlock at 8 has the rows up to its pact slot level.
        assertThat(service.preview(pc("Paladin", 3)).alwaysPreparedSpellsByFeat().get("Abjuration Adept"))
                .containsExactly(new SpellGrant("Shield", "Abjuration Adept"));
        assertThat(service.preview(pc("Warlock", 7)).alwaysPreparedSpellsByFeat().get("Necromancy Adept"))
                .extracting(SpellGrant::name)
                .containsExactly("Inflict Wounds", "Ray of Enfeeblement", "Vampiric Touch", "Blight");

        // Warlike Familiar is listed only for a character it is offered to.
        PC withFamiliar = pc("Wizard", 3);
        withFamiliar.setFeat("Familiar Friend");
        withFamiliar.setSpells("[{\"lvl\":1,\"name\":\"Find Familiar\",\"alwaysPrepared\":true}]");
        assertThat(service.preview(withFamiliar).alwaysPreparedSpellsByFeat().get("Warlike Familiar"))
                .containsExactly(new SpellGrant("Battle Familiar", "Warlike Familiar"));

        // A spell already held always prepared is left out; a feat that adds nothing has no entry.
        PC abjurer = pc("Wizard", 3);
        abjurer.setSpells("[{\"lvl\":1,\"name\":\"shield\",\"alwaysPrepared\":true}]");
        assertThat(service.preview(abjurer).alwaysPreparedSpellsByFeat().get("Abjuration Adept"))
                .containsExactly(new SpellGrant("Lesser Restoration", "Abjuration Adept"));

        assertThat(service.preview(pc("Fighter", 3)).alwaysPreparedSpellsByFeat()).isEmpty();
        assertThat(service.preview(pc("Wizard", 4)).alwaysPreparedSpellsByFeat()).isEmpty(); // -> 5: no feat
    }

    @Test
    void apply_takingASchoolAdept_savesItsRows_andLaterLevelUpsBringTheNextRows() {
        PC wizard = pc("Wizard", 3);
        service.applyLevelUp(wizard, null, null, "Evocation Adept", List.of(
                spell(1, "Chromatic Orb", "evocation"), spell(2, "Shatter", "evocation"),
                spell(2, "Web", "conjuration"), spell(0, "Light", "evocation"))); // the level's own picks
        assertThat(wizard.getLevel()).isEqualTo((short) 4);
        for (String granted : List.of("Chromatic Orb", "Shatter")) {
            assertThat(saved(wizard, granted)).as(granted)
                    .containsEntry("alwaysPrepared", true).containsEntry("grantedBy", "Evocation Adept");
        }
        assertThat(saved(wizard, "Web")).doesNotContainKey("alwaysPrepared");

        // 4 -> 5: level 3 slots, so the third row is owed.
        assertThat(service.preview(wizard).alwaysPreparedSpells())
                .containsExactly(new SpellGrant("Fireball", "Evocation Adept"));
        service.applyLevelUp(wizard, null, null, null, List.of(spell(3, "Fireball", "evocation")));
        assertThat(saved(wizard, "Fireball")).containsEntry("grantedBy", "Evocation Adept");
        assertThat(service.preview(wizard).alwaysPreparedSpells()).isEmpty(); // 5 -> 6: no new slot level
    }

    @Test
    void apply_marksASpellTheCharacterAlreadyHas_insteadOfAddingItTwice() {
        PC wizard = pc("Wizard", 3);
        wizard.setSpells("[{\"lvl\":1,\"name\":\"Chromatic Orb\",\"prepared\":false}]");
        service.applyLevelUp(wizard, null, null, "Evocation Adept", List.of(
                spell(1, "chromatic orb", "evocation"), spell(2, "Shatter", "evocation")));
        assertThat(spellsOf(wizard)).hasSize(2);
        assertThat(saved(wizard, "Chromatic Orb"))
                .containsEntry("prepared", true).containsEntry("alwaysPrepared", true)
                .containsEntry("grantedBy", "Evocation Adept");
    }

    @Test
    void apply_aRowTheCharacterHasNoSlotsFor_isAnOrdinaryPick_andHitsTheSlotCap() {
        PC wizard = pc("Wizard", 3);
        assertRefused(() -> service.applyLevelUp(wizard, null, null, "Evocation Adept",
                        List.of(spell(3, "Fireball", "evocation"))),
                "'Fireball' is a level-3 spell — the highest spell level castable at level 4 is 2");
        // The picks beside a feat's spells are still counted: a wizard learns one spell at 4.
        PC greedy = pc("Wizard", 3);
        assertRefused(() -> service.applyLevelUp(greedy, null, null, "Evocation Adept", List.of(
                        spell(1, "Chromatic Orb", "evocation"), spell(1, "Sleep", "enchantment"),
                        spell(2, "Web", "conjuration"))),
                "You can learn at most 1 new spell(s) at level 4");
    }

    @Test
    void apply_warlikeFamiliar_givesBattleFamiliar_evenToACharacterWithoutSpellSlots() {
        PC rogue = pc("Rogue", 3);
        rogue.setFeat("Familiar Friend");
        rogue.setSpells("[{\"lvl\":1,\"name\":\"Find Familiar\",\"alwaysPrepared\":true,\"grantedBy\":\"Familiar Friend\"}]");
        service.applyLevelUp(rogue, null, null, "Warlike Familiar",
                List.of(spell(2, "Battle Familiar", "transmutation")));
        assertThat(saved(rogue, "Battle Familiar"))
                .containsEntry("alwaysPrepared", true).containsEntry("grantedBy", "Warlike Familiar");
    }

    // ── Arcane Warrior: two Wizard cantrips ──────────────────────────────────────

    @Test
    void arcaneWarrior_asAFeat_needsItsTwoCantripsMarkedForIt() {
        PC fighter = pc("Fighter", 3);
        service.applyLevelUp(fighter, null, null, "Arcane Warrior", List.of(
                pick(0, "Mage Hand", "conjuration", "Arcane Warrior"),
                pick(0, "Ray of Frost", "evocation", "arcane warrior")));
        assertThat(fighter.getFeatures()).contains(feature("Arcane Warrior", "Feat (Level 4)"));
        assertThat(spellsOf(fighter)).hasSize(2);
        assertThat(saved(fighter, "Ray of Frost"))
                .containsEntry("grantedBy", "Arcane Warrior").doesNotContainKey("alwaysPrepared");

        String needs = "Arcane Warrior requires two cantrips";
        assertRefused(() -> service.applyLevelUp(pc("Fighter", 3), null, null, "Arcane Warrior"), needs);
        assertRefused(() -> service.applyLevelUp(pc("Fighter", 3), null, null, "Arcane Warrior",
                List.of(pick(0, "Mage Hand", "conjuration", "Arcane Warrior"))), needs);
        assertRefused(() -> service.applyLevelUp(pc("Fighter", 3), null, null, "Arcane Warrior", List.of(
                pick(0, "Mage Hand", "conjuration", "Arcane Warrior"),
                pick(0, "mage hand", "conjuration", "Arcane Warrior"))), needs);
        assertRefused(() -> service.applyLevelUp(pc("Fighter", 3), null, null, "Arcane Warrior", List.of(
                pick(0, "Mage Hand", "conjuration", "Arcane Warrior"),
                pick(1, "Shield", "abjuration", "Arcane Warrior"))), needs);
        // Unmarked cantrips are the class's own picks, and a Fighter has none.
        assertRefused(() -> service.applyLevelUp(pc("Fighter", 3), null, null, "Arcane Warrior", List.of(
                spell(0, "Mage Hand", "conjuration"), spell(0, "Ray of Frost", "evocation"))), needs);

        PC knows = pc("Fighter", 3);
        knows.setSpells("[{\"lvl\":0,\"name\":\"Mage Hand\",\"prepared\":true}]");
        assertRefused(() -> service.applyLevelUp(knows, null, null, "Arcane Warrior", List.of(
                pick(0, "Mage Hand", "conjuration", "Arcane Warrior"),
                pick(0, "Ray of Frost", "evocation", "Arcane Warrior"))), "'Mage Hand' is already known");
    }

    @Test
    void arcaneWarrior_asTheLevel2FightingStyle_needsExactlyTwoCantrips() {
        PC paladin = pc("Paladin", 1);
        service.applyLevelUp(paladin, null, null, null, List.of(
                spell(0, "Mage Hand", "conjuration"), spell(0, "Ray of Frost", "evocation"),
                spell(1, "Bless", "enchantment")), null, null, "Arcane Warrior");
        assertThat(paladin.getFeatures()).contains("\"name\":\"Arcane Warrior\",\"source\":\"Fighting Style (Level 2)\"")
                .contains("two Wizard cantrips");
        assertThat(spellsOf(paladin)).extracting(s -> s.get("name"))
                .contains("Mage Hand", "Ray of Frost", "Bless");
        assertThat(CharacterFeats.namesOn(paladin)).contains("Arcane Warrior");
        // Having it as the Fighting Style, the paladin is not offered it as a feat at 4.
        paladin.setLevel((short) 3);
        assertThat(service.preview(paladin).featOptions()).doesNotContain("Arcane Warrior").contains("Archery");

        assertRefused(() -> service.applyLevelUp(pc("Ranger", 1), null, null, null, null, null, null,
                "Arcane Warrior"), "Arcane Warrior requires exactly 2 new cantrip(s) at level 2");
        assertRefused(() -> service.applyLevelUp(pc("Ranger", 1), null, null, null,
                        List.of(spell(0, "Mage Hand", "conjuration")), null, null, "Arcane Warrior"),
                "Arcane Warrior requires exactly 2 new cantrip(s) at level 2");
    }

    // ── Magic Connoisseur: a level 1 and a level 2 spell ─────────────────────────

    @Test
    void magicConnoisseur_savesItsTwoPicksAlwaysPrepared() {
        PC fighter = pc("Fighter", 3);
        fighter.setFeat("Magic Initiate (Wizard)");
        fighter.setSpells("[{\"lvl\":1,\"name\":\"Shield\",\"prepared\":true,\"alwaysPrepared\":true,"
                + "\"grantedBy\":\"Magic Initiate (Wizard)\"}]");
        service.applyLevelUp(fighter, null, null, "Magic Connoisseur", List.of(
                pick(1, "Magic Missile", "evocation", "Magic Connoisseur"),
                pick(2, "Misty Step", "conjuration", "Magic Connoisseur")));
        for (String name : List.of("Magic Missile", "Misty Step")) { // no slots: outside the slot cap
            assertThat(saved(fighter, name)).as(name)
                    .containsEntry("alwaysPrepared", true).containsEntry("grantedBy", "Magic Connoisseur")
                    .containsEntry("prepared", true);
        }
        assertThat(saved(fighter, "Shield")).containsEntry("grantedBy", "Magic Initiate (Wizard)");
    }

    @Test
    void magicConnoisseur_refusesAnythingButOneLevel1AndOneLevel2Spell() {
        String needs = "Magic Connoisseur requires one level 1 spell and one level 2 spell";
        List<List<Map<String, Object>>> wrong = List.of(
                List.of(),
                List.of(pick(1, "Magic Missile", "evocation", "Magic Connoisseur")),
                List.of(pick(1, "Magic Missile", "evocation", "Magic Connoisseur"),
                        pick(1, "Sleep", "enchantment", "Magic Connoisseur")),
                List.of(pick(1, "Magic Missile", "evocation", "Magic Connoisseur"),
                        pick(3, "Fireball", "evocation", "Magic Connoisseur")),
                List.of(pick(1, "Magic Missile", "evocation", "Magic Connoisseur"),
                        pick(2, "Misty Step", "conjuration", "Magic Connoisseur"),
                        pick(2, "Web", "conjuration", "Magic Connoisseur")));
        for (List<Map<String, Object>> picks : wrong) {
            PC fighter = pc("Fighter", 3);
            fighter.setFeat("Magic Initiate (Wizard)");
            assertRefused(() -> service.applyLevelUp(fighter, null, null, "Magic Connoisseur", picks), needs);
            assertThat(fighter.getLevel()).isEqualTo((short) 3);
        }
    }

    // ── Boon of Magic School Mastery: two spells of one school ───────────────────

    @Test
    void schoolMastery_savesALevel1SpellAndASpellOfLevel7OrLower_ofItsSchool() {
        String boon = "Boon of Magic School Mastery (Evocation)";
        PC paladin = pc("Paladin", 18); // level 5 slots at 19: the level 7 pick is outside the slot cap
        service.applyLevelUp(paladin, null, null, boon, List.of(
                pick(1, "Thunderous Smite", "Evocation", boon),
                pick(7, "Delayed Blast Fireball", "evocation", boon)));
        assertThat(paladin.getFeatures()).contains(feature(boon, "Feat (Level 19)"));
        for (String name : List.of("Thunderous Smite", "Delayed Blast Fireball")) {
            assertThat(saved(paladin, name)).as(name)
                    .containsEntry("alwaysPrepared", true).containsEntry("grantedBy", boon);
        }
        // The same school is not offered again; the other seven still are.
        paladin.setLevel((short) 18);
        assertThat(service.preview(paladin).featOptions())
                .doesNotContain(boon).contains("Boon of Magic School Mastery (Abjuration)");
    }

    @Test
    void schoolMastery_refusesTheWrongSchoolLevelOrNumber() {
        String boon = "Boon of Magic School Mastery (Evocation)";
        String needs = boon + " requires one level 1 Evocation spell and one Evocation spell of level 7 or lower";
        assertRefused(() -> service.applyLevelUp(pc("Wizard", 18), null, null, boon), needs);
        assertRefused(() -> service.applyLevelUp(pc("Wizard", 18), null, null, boon, List.of(
                pick(2, "Shatter", "evocation", boon), pick(3, "Fireball", "evocation", boon))), needs);
        assertRefused(() -> service.applyLevelUp(pc("Wizard", 18), null, null, boon, List.of(
                pick(1, "Magic Missile", "evocation", boon), pick(8, "Sunburst", "evocation", boon))), needs);
        assertRefused(() -> service.applyLevelUp(pc("Wizard", 18), null, null, boon, List.of(
                pick(1, "Magic Missile", "evocation", boon), pick(1, "magic missile", "evocation", boon))), needs);
        assertRefused(() -> service.applyLevelUp(pc("Wizard", 18), null, null, boon, List.of(
                pick(1, "Magic Missile", "evocation", boon), pick(1, "Shield", "abjuration", boon))),
                "'Shield' is not a spell of the Evocation school");
        Map<String, Object> noSchool = pick(3, "Fireball", "evocation", boon);
        noSchool.remove("school");
        assertRefused(() -> service.applyLevelUp(pc("Wizard", 18), null, null, boon, List.of(
                pick(1, "Magic Missile", "evocation", boon), noSchool)),
                "'Fireball' is not a spell of the Evocation school");
    }
}
