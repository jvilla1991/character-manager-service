package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.SpellGrant;
import com.moo.charactermanagerservice.progression.FeatSpellGrants.PickRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The spells the Arcana Unleashed feats give: named spells, School Adept tables, and picks. */
class FeatSpellGrantsTest {

    private static List<String> names(List<SpellGrant> grants) {
        return grants.stream().map(SpellGrant::name).toList();
    }

    // ── Named spells ─────────────────────────────────────────────────────────────

    // feat => spell
    private static final String NAMED = """
            Arcane Artist => Minor Illusion
            Arcane Eloquence => Vicious Mockery
            Arcane Infiltrator => Friends
            Arcane Omens => Guidance
            Arcane Overload => Fire Bolt
            Arcane Safeguard => Resistance
            Familiar Friend => Find Familiar
            Warlike Familiar => Battle Familiar
            """;

    @Test
    void aFeatThatNamesASpell_grantsIt_whateverTheClassAndLevel() {
        int checked = 0;
        for (String line : NAMED.strip().split("\\R")) {
            String[] sides = line.split(" => ");
            for (String clazz : List.of("Fighter", "Wizard", "Warlock")) {
                assertThat(FeatSpellGrants.grantsFor(sides[0], clazz, null, 1)).as(line)
                        .containsExactly(new SpellGrant(sides[1], sides[0]));
            }
            // The stored name's case and spacing do not matter; the grant carries the feat's own name.
            assertThat(FeatSpellGrants.grantsFor("  " + sides[0].toUpperCase() + " ", "Rogue", null, 20))
                    .containsExactly(new SpellGrant(sides[1], sides[0]));
            checked++;
        }
        assertThat(checked).isEqualTo(8);
    }

    @Test
    void otherFeats_grantNoSpellByName() {
        for (String feat : List.of("Arcane Undertaker", "Portal Jumper", "Transmuted Anatomy", "Elemental Familiar",
                "Otherworldly Familiar", "Soothing Familiar", "Magic Connoisseur", "Spell Resistant",
                "Spell Subterfuge", "Arcane Warrior", "Boon of Erupting Spellpower", "Boon of the Iron Mind",
                "Boon of Magic School Mastery (Evocation)", "Magic Initiate (Wizard)", "Alert", "Second Wind")) {
            assertThat(FeatSpellGrants.grantsFor(feat, "Wizard", null, 20)).as(feat).isEmpty();
        }
        assertThat(FeatSpellGrants.grantsFor(null, "Wizard", null, 20)).isEmpty();
    }

    // ── School Adept tables ──────────────────────────────────────────────────────

    // feat => the spells for spell slot levels 1, 2, 3, 4, 5
    private static final String TABLES = """
            Abjuration Adept => Shield, Lesser Restoration, Protection from Energy, Banishment, Mass Cure Wounds
            Conjuration Adept => Entangle, Misty Step, Conjure Animals, Dimension Door, Conjure Elemental
            Divination Adept => Detect Evil and Good, Mind Spike, Clairvoyance, Divination, Scrying
            Enchantment Adept => Dissonant Whispers, Enthrall, Hold Person, Dominate Beast, Modify Memory
            Evocation Adept => Chromatic Orb, Shatter, Fireball, Vitriolic Sphere, Wall of Force
            Illusion Adept => Silent Image, Phantasmal Force, Major Image, Hallucinatory Terrain, Seeming
            Necromancy Adept => Inflict Wounds, Ray of Enfeeblement, Vampiric Touch, Blight, Raise Dead
            Transmutation Adept => Jump, Spider Climb, Slow, Polymorph, Animate Objects
            """;

    @Test
    void eachSchoolAdept_hasItsFiveSpells_oneForEachSlotLevel() {
        int checked = 0;
        for (String line : TABLES.strip().split("\\R")) {
            String[] sides = line.split(" => ");
            List<String> spells = List.of(sides[1].split(", "));
            assertThat(spells).hasSize(5);
            assertThat(FeatSpellGrants.schoolAdeptSpells(sides[0])).as(sides[0]).isEqualTo(spells);
            // A level 20 wizard has every row, each granted by the feat.
            assertThat(FeatSpellGrants.grantsFor(sides[0], "Wizard", null, 20)).as(sides[0])
                    .containsExactly(spells.stream().map(s -> new SpellGrant(s, sides[0])).toArray(SpellGrant[]::new));
            checked++;
        }
        assertThat(checked).isEqualTo(8);
    }

    @Test
    void aFullCaster_gainsARowAtEachNewSlotLevel_upToFive() {
        // Wizard: slot levels 1, 2, 3, 4, 5 arrive at character levels 1, 3, 5, 7, 9.
        assertThat(names(FeatSpellGrants.grantsFor("Evocation Adept", "Wizard", null, 4)))
                .containsExactly("Chromatic Orb", "Shatter");
        assertThat(names(FeatSpellGrants.grantsFor("Evocation Adept", "Wizard", null, 5)))
                .containsExactly("Chromatic Orb", "Shatter", "Fireball");
        assertThat(names(FeatSpellGrants.grantsFor("Evocation Adept", "Cleric", null, 6))).hasSize(3);
        assertThat(names(FeatSpellGrants.grantsFor("Evocation Adept", "Bard", null, 7))).hasSize(4);
        assertThat(names(FeatSpellGrants.grantsFor("Evocation Adept", "Druid", null, 8))).hasSize(4);
        assertThat(names(FeatSpellGrants.grantsFor("Evocation Adept", "Sorcerer", null, 9)))
                .containsExactly("Chromatic Orb", "Shatter", "Fireball", "Vitriolic Sphere", "Wall of Force");
        // Slot levels 6 to 9 add nothing: the table stops at 5.
        assertThat(names(FeatSpellGrants.grantsFor("Evocation Adept", "Wizard", null, 17))).hasSize(5);
    }

    @Test
    void aHalfCaster_gainsTheRowsMoreSlowly() {
        // Paladin and Ranger: slot levels 1, 2, 3, 4, 5 arrive at character levels 1, 5, 9, 13, 17.
        int[][] expected = {{4, 1}, {5, 2}, {8, 2}, {9, 3}, {12, 3}, {13, 4}, {16, 4}, {17, 5}, {20, 5}};
        for (int[] row : expected) {
            for (String clazz : List.of("Paladin", "Ranger")) {
                assertThat(names(FeatSpellGrants.grantsFor("Abjuration Adept", clazz, null, row[0])))
                        .as(clazz + " " + row[0]).hasSize(row[1]);
            }
        }
        assertThat(names(FeatSpellGrants.grantsFor("Abjuration Adept", "Paladin", null, 9)))
                .containsExactly("Shield", "Lesser Restoration", "Protection from Energy");
    }

    @Test
    void aWarlock_hasTheRowsUpToItsPactSlotLevel() {
        // Pact Magic: one slot level at a time (1, 2, 3, 4, 5 at levels 1, 3, 5, 7, 9); the rows
        // for the lower levels stay.
        int[][] expected = {{2, 1}, {3, 2}, {4, 2}, {5, 3}, {6, 3}, {7, 4}, {8, 4}, {9, 5}, {20, 5}};
        for (int[] row : expected) {
            assertThat(names(FeatSpellGrants.grantsFor("Necromancy Adept", "Warlock", null, row[0])))
                    .as("Warlock " + row[0]).hasSize(row[1]);
        }
        assertThat(names(FeatSpellGrants.grantsFor("Necromancy Adept", "Warlock", null, 5)))
                .containsExactly("Inflict Wounds", "Ray of Enfeeblement", "Vampiric Touch");
    }

    @Test
    void aCharacterWithoutSpellSlots_getsNoRows() {
        assertThat(FeatSpellGrants.grantsFor("Illusion Adept", "Fighter", null, 20)).isEmpty();
        assertThat(FeatSpellGrants.grantsFor("Illusion Adept", null, null, 20)).isEmpty();
    }

    // ── Everything a character's feats give ──────────────────────────────────────

    @Test
    void grantsThrough_addsUpTheFeatsHeld_inOrder_eachSpellOnce() {
        List<String> feats = List.of("Sage", "Familiar Friend", "Second Wind", "Evocation Adept", "familiar friend",
                "Warlike Familiar", "Divination Adept");
        assertThat(FeatSpellGrants.grantsThrough(feats, "Wizard", null, 4)).containsExactly(
                new SpellGrant("Find Familiar", "Familiar Friend"),
                new SpellGrant("Chromatic Orb", "Evocation Adept"),
                new SpellGrant("Shatter", "Evocation Adept"),
                new SpellGrant("Battle Familiar", "Warlike Familiar"),
                new SpellGrant("Detect Evil and Good", "Divination Adept"),
                new SpellGrant("Mind Spike", "Divination Adept"));
        // A level later the third rows arrive: that is what a level-up hands over.
        assertThat(names(FeatSpellGrants.grantsThrough(feats, "Wizard", null, 5)))
                .contains("Fireball", "Clairvoyance").hasSize(8);
        assertThat(FeatSpellGrants.grantsThrough(List.of(), "Wizard", null, 5)).isEmpty();
        assertThat(FeatSpellGrants.grantsThrough(null, "Wizard", null, 5)).isEmpty();
    }

    // ── Picks ────────────────────────────────────────────────────────────────────

    @Test
    void arcaneWarrior_picksTwoCantrips() {
        PickRule rule = FeatSpellGrants.pickRule(" arcane warrior ").orElseThrow();
        assertThat(rule.feat()).isEqualTo("Arcane Warrior");
        assertThat(rule.school()).isNull();
        assertThat(rule.needs()).isEqualTo("two cantrips");
        assertThat(rule.satisfiedBy(List.of(0, 0))).isTrue();
        assertThat(rule.satisfiedBy(List.of(0))).isFalse();
        assertThat(rule.satisfiedBy(List.of(0, 0, 0))).isFalse();
        assertThat(rule.satisfiedBy(List.of(0, 1))).isFalse();
        assertThat(rule.satisfiedBy(List.of())).isFalse();
    }

    @Test
    void magicConnoisseur_picksOneLevel1AndOneLevel2Spell() {
        PickRule rule = FeatSpellGrants.pickRule("Magic Connoisseur").orElseThrow();
        assertThat(rule.school()).isNull();
        assertThat(rule.needs()).isEqualTo("one level 1 spell and one level 2 spell");
        assertThat(rule.satisfiedBy(List.of(1, 2))).isTrue();
        assertThat(rule.satisfiedBy(List.of(2, 1))).isTrue();
        assertThat(rule.satisfiedBy(List.of(1, 1))).isFalse();
        assertThat(rule.satisfiedBy(List.of(2, 2))).isFalse();
        assertThat(rule.satisfiedBy(List.of(1, 3))).isFalse();
        assertThat(rule.satisfiedBy(List.of(0, 2))).isFalse();
        assertThat(rule.satisfiedBy(List.of(1))).isFalse();
    }

    @Test
    void schoolMastery_picksALevel1SpellAndASpellOfLevel7OrLower_ofItsSchool() {
        for (String school : FeatCatalog.SCHOOLS_OF_MAGIC) {
            PickRule rule = FeatSpellGrants.pickRule(FeatCatalog.schoolMasteryFeat(school)).orElseThrow();
            assertThat(rule.feat()).isEqualTo("Boon of Magic School Mastery (" + school + ")");
            assertThat(rule.school()).isEqualTo(school);
            assertThat(rule.needs()).isEqualTo(
                    "one level 1 " + school + " spell and one " + school + " spell of level 7 or lower");
            assertThat(rule.satisfiedBy(List.of(1, 7))).isTrue();
            assertThat(rule.satisfiedBy(List.of(5, 1))).isTrue();
            assertThat(rule.satisfiedBy(List.of(1, 1))).isTrue();
            assertThat(rule.satisfiedBy(List.of(2, 3))).isFalse(); // no level 1 spell
            assertThat(rule.satisfiedBy(List.of(1, 8))).isFalse();
            assertThat(rule.satisfiedBy(List.of(1, 0))).isFalse(); // a cantrip is not a spell of level 1 to 7
            assertThat(rule.satisfiedBy(List.of(1))).isFalse();
        }
        assertThat(FeatSpellGrants.pickRule("Boon of Magic School Mastery")).isEmpty();
    }

    @Test
    void otherFeats_haveNoPicks() {
        for (String feat : List.of("Evocation Adept", "Familiar Friend", "Arcane Undertaker", "Spell Resistant",
                "War Caster", "Blessed Warrior")) {
            assertThat(FeatSpellGrants.pickRule(feat)).as(feat).isEmpty();
        }
        assertThat(FeatSpellGrants.pickRule(null)).isEmpty();
    }
}
