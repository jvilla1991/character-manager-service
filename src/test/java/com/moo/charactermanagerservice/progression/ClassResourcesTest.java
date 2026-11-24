package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.progression.ClassResources.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The class resource rules table, case by case.
 *
 * <p>{@link #CASES} is the contract between the server and the client: the
 * frontend's {@code class-resources.spec.ts} carries the SAME block of text and
 * runs it against its own copy of the table. Change a rule and both blocks must
 * change — keep them identical, line for line.
 */
class ClassResourcesTest {

    // class | subclass | level | CHA | WIS  =>  key:max:shortRest, ...   ("-" = none)
    static final String CASES = """
            Fighter||1|10|10 => secondWind:2:ONE
            Fighter||2|10|10 => secondWind:2:ONE,actionSurge:1:ALL
            Fighter||4|10|10 => secondWind:3:ONE,actionSurge:1:ALL
            Fighter||10|10|10 => secondWind:4:ONE,actionSurge:1:ALL
            Fighter||17|10|10 => secondWind:4:ONE,actionSurge:2:ALL
            Fighter|Battle Master|7|10|10 => secondWind:3:ONE,actionSurge:1:ALL
            Barbarian||1|10|10 => rage:2:ONE
            Barbarian||3|10|10 => rage:3:ONE
            Barbarian||6|10|10 => rage:4:ONE
            Barbarian||12|10|10 => rage:5:ONE
            Barbarian||17|10|10 => rage:6:ONE
            Barbarian|Path of the Elements|10|10|10 => rage:4:ONE
            Bard||1|16|10 => bardicInspiration:3:NONE
            Bard||4|8|10 => bardicInspiration:1:NONE
            Bard||5|18|10 => bardicInspiration:4:ALL
            Cleric||1|10|10 => -
            Cleric||2|10|10 => channelDivinity:2:ONE
            Cleric||6|10|10 => channelDivinity:3:ONE
            Cleric||18|10|10 => channelDivinity:4:ONE
            Druid||1|10|10 => -
            Druid||2|10|10 => wildShape:2:ONE
            Druid||6|10|10 => wildShape:3:ONE
            Druid||17|10|10 => wildShape:4:ONE
            Monk||1|10|10 => -
            Monk||2|10|10 => focusPoints:2:ALL
            Monk||11|10|10 => focusPoints:11:ALL
            Paladin||1|10|10 => layOnHands:5:NONE
            Paladin||3|10|10 => layOnHands:15:NONE,channelDivinity:2:ONE
            Paladin||11|10|10 => layOnHands:55:NONE,channelDivinity:3:ONE
            Paladin|Oath of the Holy|7|16|10 => layOnHands:35:NONE,channelDivinity:2:ONE
            Paladin|Oath of the Ebon Blade|1|16|10 => deathCoil:4:NONE
            Paladin|Oath of the Ebon Blade|5|8|10 => deathCoil:1:NONE,channelDivinity:2:ONE
            Paladin|oath of the ebon blade|11|20|10 => deathCoil:6:NONE,channelDivinity:3:ONE
            Ranger||5|10|16 => -
            Ranger|Demon Hunter|2|10|16 => -
            Ranger|Demon Hunter|3|10|16 => soulFragments:3:NONE
            Ranger|Demon Hunter|13|10|8 => soulFragments:2:NONE
            Ranger|Demon Hunter|17|10|18 => soulFragments:6:NONE
            Sorcerer||1|10|10 => -
            Sorcerer||2|10|10 => sorceryPoints:2:NONE
            Sorcerer||20|10|10 => sorceryPoints:20:NONE
            Warlock||5|10|10 => -
            Wizard||5|10|10 => -
            Rogue||5|10|10 => -
            """;

    /**
     * The feat counters, case by case — the second block the frontend's
     * {@code class-resources.spec.ts} carries word for word.
     */
    // level | INT | WIS | CHA | feats ("; " between)  =>  key:max:shortRest, ...   ("-" = none)
    static final String FEAT_CASES = """
            1|10|10|10|Arcane Artist => inspiringMagic:1:NONE
            1|10|10|10|Arcane Eloquence => -
            1|10|10|10|Arcane Infiltrator => cunningDiversion:2:NONE
            5|10|10|10|Arcane Infiltrator => cunningDiversion:3:NONE
            9|10|10|10|Arcane Omens => helpfulPremonition:4:NONE
            1|10|10|10|Arcane Overload => powerSurge:1:NONE
            13|10|10|10|Arcane Safeguard => bonusActionResistance:5:NONE
            1|10|10|10|Arcane Undertaker => understandingOfDeath:1:NONE
            1|10|10|10|Familiar Friend => findFamiliarFreeCast:1:NONE,helpfulFriend:2:NONE
            17|10|10|10|Familiar Friend => findFamiliarFreeCast:1:NONE,helpfulFriend:6:NONE
            4|10|10|10|Portal Jumper => portalStep:2:NONE
            8|10|10|10|Transmuted Anatomy => resilientAnatomy:3:NONE
            4|10|10|10|Divination Adept => prescientIntervention:1:NONE
            4|10|10|10|Abjuration Adept; Conjuration Adept; Enchantment Adept; Evocation Adept => -
            4|10|10|10|Illusion Adept; Necromancy Adept; Transmutation Adept => -
            4|10|10|10|Magic Initiate (Wizard); Magic Connoisseur => connoisseurLevel1Spell:1:NONE,connoisseurLevel2Spell:1:NONE
            4|10|10|10|Spell Resistant => magicResistant:2:NONE
            20|10|10|10|Spell Resistant => magicResistant:6:NONE
            4|8|8|8|Spell Subterfuge => shroudingSpells:1:NONE
            4|16|12|10|Spell Subterfuge => shroudingSpells:3:NONE
            8|10|20|14|Spell Subterfuge => shroudingSpells:5:NONE
            8|10|12|18|Spell Subterfuge => shroudingSpells:4:NONE
            4|10|10|10|Familiar Friend; Warlike Familiar => findFamiliarFreeCast:1:NONE,helpfulFriend:2:NONE,battleFamiliarFreeCast:1:NONE
            4|10|10|10|Elemental Familiar; Otherworldly Familiar; Soothing Familiar => -
            19|10|10|10|Arcane Warrior; Boon of the Iron Mind => -
            19|10|10|10|Boon of Erupting Spellpower => spellOverload:1:ALL
            19|10|10|10|Boon of Magic School Mastery (Evocation) => signatureArcanumEvocation:1:NONE
            20|10|10|10|Boon of Magic School Mastery (Illusion); Boon of Magic School Mastery (Abjuration) => signatureArcanumIllusion:1:NONE,signatureArcanumAbjuration:1:NONE
            19|10|10|10|Boon of Magic School Mastery => -
            5|10|10|10|arcane artist;  PORTAL JUMPER ; Arcane Artist => inspiringMagic:1:NONE,portalStep:3:NONE
            5|10|10|10|Alert; Lucky; Second Wind; Magic Initiate (Cleric) => -
            5|10|10|10| => -
            """;

    private static List<String> feats(String cell) {
        return cell.isBlank() ? List.of() : List.of(cell.split(";"));
    }

    @Test
    void everyFeatCase_givesExactlyTheCountersTheRulesSay() {
        int checked = 0;
        for (String line : FEAT_CASES.strip().split("\\R")) {
            String[] sides = line.split(" => ");
            String[] in = sides[0].split("\\|", -1);
            List<Resource> actual = ClassResources.forFeats(feats(in[4]), Integer.parseInt(in[0]),
                    Integer.parseInt(in[1]), Integer.parseInt(in[2]), Integer.parseInt(in[3]));
            assertThat(describe(actual)).as(sides[0]).isEqualTo(sides[1]);
            checked++;
        }
        assertThat(checked).isEqualTo(32);
    }

    @Test
    void featCounters_followTheClassResources_andBelongToTheFeat() {
        List<Resource> wizard = ClassResources.forCharacter("Wizard", null, 4, 10, 10, 16,
                List.of("Familiar Friend", "Arcane Recovery", "Spell Subterfuge"));
        assertThat(describe(wizard))
                .isEqualTo("findFamiliarFreeCast:1:NONE,helpfulFriend:2:NONE,shroudingSpells:3:NONE");
        assertThat(wizard).extracting(Resource::feature)
                .containsExactly("Familiar Friend", "Familiar Friend", "Spell Subterfuge");
        assertThat(wizard).extracting(Resource::name)
                .containsExactly("Find Familiar (free cast)", "Helpful Friend", "Shrouding Spells");
        assertThat(wizard).allMatch(r -> !r.pool() && r.unit().equals("uses"));

        List<Resource> fighter = ClassResources.forCharacter("Fighter", null, 5, 10, 10, 10, List.of("Portal Jumper"));
        assertThat(describe(fighter)).isEqualTo("secondWind:3:ONE,actionSurge:1:ALL,portalStep:3:NONE");

        Resource arcanum = ClassResources.forFeats(List.of("boon of magic school mastery (necromancy)"), 19, 10, 10, 10)
                .get(0);
        assertThat(arcanum.name()).isEqualTo("Signature Arcanum (Necromancy)");
        assertThat(arcanum.feature()).isEqualTo("Boon of Magic School Mastery (Necromancy)");

        // Without feats, or with none recorded, a character has its class's resources and no more.
        assertThat(describe(ClassResources.forCharacter("Fighter", null, 1, 10, 10, null, null)))
                .isEqualTo("secondWind:2:ONE");
        assertThat(ClassResources.forFeats(null, 5, 10, 10, 10)).isEmpty();
        assertThat(describe(ClassResources.forFeats(List.of("Spell Subterfuge", "Spell Resistant"), null, null, null, null)))
                .isEqualTo("shroudingSpells:1:NONE,magicResistant:2:NONE");
    }

    private static String describe(List<Resource> resources) {
        return resources.isEmpty() ? "-" : resources.stream()
                .map(r -> r.key() + ":" + r.max() + ":" + r.shortRest())
                .collect(Collectors.joining(","));
    }

    @Test
    void everyCase_givesExactlyTheResourcesTheRulesSay() {
        int checked = 0;
        for (String line : CASES.strip().split("\\R")) {
            String[] sides = line.split(" => ");
            String[] in = sides[0].split("\\|", -1);
            List<Resource> actual = ClassResources.forCharacter(in[0], in[1].isEmpty() ? null : in[1],
                    Integer.parseInt(in[2]), Integer.parseInt(in[3]), Integer.parseInt(in[4]));
            assertThat(describe(actual)).as(sides[0]).isEqualTo(sides[1]);
            checked++;
        }
        assertThat(checked).isEqualTo(44);
    }

    @Test
    void poolsAndUses_areToldApart_andNamedForTheSheet() {
        Resource lay = ClassResources.forCharacter("Paladin", null, 4, 10, 10).get(0);
        assertThat(lay.pool()).isTrue();
        assertThat(lay.name()).isEqualTo("Lay On Hands");
        assertThat(lay.unit()).isEqualTo("points");

        Resource wind = ClassResources.forCharacter("Fighter", null, 1, 10, 10).get(0);
        assertThat(wind.pool()).isFalse();
        assertThat(wind.unit()).isEqualTo("uses");

        // Points belong to the feature that grants them, as the feature lists name it.
        assertThat(ClassResources.forCharacter("Sorcerer", null, 3, 10, 10).get(0).feature()).isEqualTo("Font of Magic");
        assertThat(ClassResources.forCharacter("Monk", null, 3, 10, 10).get(0).feature()).isEqualTo("Monk's Focus");
        assertThat(ClassResources.forCharacter("Paladin", "Oath of the Ebon Blade", 3, 10, 10).get(0).unit())
                .isEqualTo("d8");
    }

    @Test
    void missingValues_areHandled() {
        assertThat(ClassResources.forCharacter(null, null, 5, 10, 10)).isEmpty();
        assertThat(describe(ClassResources.forCharacter(" fighter ", null, null, null, null)))
                .isEqualTo("secondWind:2:ONE");
        assertThat(describe(ClassResources.forCharacter("Bard", null, 3, null, null)))
                .isEqualTo("bardicInspiration:1:NONE"); // no Charisma recorded: 10, so the minimum of one
    }

    @Test
    void onlyAWarlock_regainsSpellSlotsOnAShortRest() {
        assertThat(ClassResources.regainsSpellSlotsOnShortRest("Warlock")).isTrue();
        assertThat(ClassResources.regainsSpellSlotsOnShortRest(" warlock ")).isTrue();
        assertThat(ClassResources.regainsSpellSlotsOnShortRest("Wizard")).isFalse();
        assertThat(ClassResources.regainsSpellSlotsOnShortRest(null)).isFalse();
    }
}
