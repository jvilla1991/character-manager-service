package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureProgressionEntry;
import com.moo.charactermanagerservice.dto.SpellGrant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClassSpellGrantsTest {

    private static final List<String> CLASSES = List.of("barbarian", "bard", "cleric", "druid", "fighter",
            "monk", "paladin", "ranger", "rogue", "sorcerer", "warlock", "wizard");

    private static List<String> names(List<SpellGrant> grants) {
        return grants.stream().map(SpellGrant::name).toList();
    }

    // --- class features ---

    @Test
    void paladin_gainsDivineSmiteAtTwo_andFindSteedAtFive() {
        assertThat(ClassSpellGrants.grantsAt("Paladin", null, null, 2))
                .containsExactly(new SpellGrant("Divine Smite", "Divine Smite"));
        assertThat(ClassSpellGrants.grantsAt(" paladin ", null, null, 5))
                .containsExactly(new SpellGrant("Find Steed", "Faithful Steed"));
        assertThat(ClassSpellGrants.grantsAt("Paladin", null, null, 3)).isEmpty();
    }

    @Test
    void deathKnight_losesDivineSmiteWithTheFeature_butKeepsFindSteed() {
        // Runic Smite replaces Divine Smite at Paladin 2; its Rune Strike is on the hero-class table.
        assertThat(ClassSpellGrants.grantsAt("Paladin", ClassProgression.EBON_BLADE, "Frost", 2)).isEmpty();
        // The Ebon Blade's Faithful Steed still keeps Find Steed prepared.
        assertThat(ClassSpellGrants.grantsThrough("Paladin", ClassProgression.EBON_BLADE, "Frost", 20))
                .containsExactly(new SpellGrant("Find Steed", "Faithful Steed"));
    }

    @Test
    void levelOneFeatures_areListedForCatchingUp() {
        assertThat(ClassSpellGrants.grantsAt("Ranger", null, null, 1))
                .containsExactly(new SpellGrant("Hunter's Mark", "Favored Enemy"));
        assertThat(ClassSpellGrants.grantsAt("Druid", null, null, 1))
                .containsExactly(new SpellGrant("Speak with Animals", "Druidic"));
        // A Demon Hunter and a World of Warcraft Hunter are rangers: they keep Favored Enemy.
        assertThat(names(ClassSpellGrants.grantsThrough("Ranger", ClassProgression.DEMON_HUNTER, "Havoc", 20)))
                .containsExactly("Hunter's Mark");
        assertThat(names(ClassSpellGrants.grantsThrough("ranger", null, "Survival", 2)))
                .containsExactly("Hunter's Mark");
    }

    @Test
    void warlockAndBard_gainTheirFeatureSpells() {
        assertThat(ClassSpellGrants.grantsAt("Warlock", null, null, 9))
                .containsExactly(new SpellGrant("Contact Other Plane", "Contact Patron"));
        assertThat(ClassSpellGrants.grantsAt("Bard", null, null, 20))
                .containsExactly(new SpellGrant("Power Word Heal", "Words of Creation"),
                        new SpellGrant("Power Word Kill", "Words of Creation"));
    }

    // --- subclass spell lists, read from the feature text ---

    @Test
    void spellListRows_readsTheRowsOfAFeatureText() {
        assertThat(ClassSpellGrants.spellListRows("You always have these spells prepared. "
                + "3rd — Protection from Evil and Good, Shield of Faith; 5th — Aid, Zone of Truth; "
                + "17th — Commune, Flame Strike."))
                .containsExactly(
                        Map.entry(3, List.of("Protection from Evil and Good", "Shield of Faith")),
                        Map.entry(5, List.of("Aid", "Zone of Truth")),
                        Map.entry(17, List.of("Commune", "Flame Strike")));
        assertThat(ClassSpellGrants.spellListRows("You gain an extra skill proficiency.")).isEmpty();
        assertThat(ClassSpellGrants.spellListRows(null)).isEmpty();
    }

    @Test
    void oathOfDevotion_grantsEachRowAtItsLevel() {
        assertThat(ClassSpellGrants.grantsAt("Paladin", "Oath of Devotion", null, 3)).containsExactly(
                new SpellGrant("Protection from Evil and Good", "Oath of Devotion Spells"),
                new SpellGrant("Shield of Faith", "Oath of Devotion Spells"));
        assertThat(names(ClassSpellGrants.grantsAt("Paladin", "oath of devotion", null, 5)))
                .containsExactly("Aid", "Zone of Truth", "Find Steed");
        assertThat(names(ClassSpellGrants.grantsAt("Paladin", "Oath of Devotion", null, 17)))
                .containsExactly("Commune", "Flame Strike");
        // Everything owed by level 5, earliest first.
        assertThat(names(ClassSpellGrants.grantsThrough("Paladin", "Oath of Devotion", null, 5))).containsExactly(
                "Divine Smite", "Protection from Evil and Good", "Shield of Faith", "Aid", "Zone of Truth", "Find Steed");
    }

    @Test
    void otherCatalogSubclasses_grantTheirLists() {
        assertThat(names(ClassSpellGrants.grantsAt("Cleric", "Life Domain", null, 3)))
                .containsExactly("Aid", "Bless", "Cure Wounds", "Lesser Restoration");
        assertThat(names(ClassSpellGrants.grantsAt("Warlock", "Fiend Patron", null, 5)))
                .containsExactly("Fireball", "Stinking Cloud");
        assertThat(names(ClassSpellGrants.grantsAt("Ranger", "Gloom Stalker", null, 9))).containsExactly("Fear");
        assertThat(names(ClassSpellGrants.grantsAt("Druid", "Circle of the Moon", null, 3)))
                .containsExactly("Cure Wounds", "Moonbeam", "Starry Wisp");
        // The sorcerer's lists start at 3rd level even though the app picks the subclass at 1.
        assertThat(ClassSpellGrants.grantsThrough("Sorcerer", "Clockwork Sorcery", null, 2)).isEmpty();
        assertThat(names(ClassSpellGrants.grantsAt("Sorcerer", "Clockwork Sorcery", null, 3)))
                .containsExactly("Aid", "Alarm", "Lesser Restoration", "Protection from Evil and Good");
    }

    @Test
    void singleFeatureSubclassGrants_arriveWithTheFeature() {
        assertThat(ClassSpellGrants.grantsAt("Bard", "College of Glamour", null, 3)).containsExactly(
                new SpellGrant("Charm Person", "Beguiling Magic"), new SpellGrant("Mirror Image", "Beguiling Magic"));
        assertThat(names(ClassSpellGrants.grantsAt("Bard", "College of Glamour", null, 6))).containsExactly("Command");
        assertThat(names(ClassSpellGrants.grantsAt("Wizard", "Abjurer", null, 10)))
                .containsExactly("Counterspell", "Dispel Magic");
        assertThat(names(ClassSpellGrants.grantsAt("Wizard", "Illusionist", null, 6)))
                .containsExactly("Summon Beast", "Summon Fey");
        assertThat(names(ClassSpellGrants.grantsAt("Warlock", "Great Old One Patron", null, 10))).containsExactly("Hex");
        assertThat(names(ClassSpellGrants.grantsAt("Monk", "Warrior of the Elements", null, 3)))
                .containsExactly("Elementalism");
        // Another subclass of the same class gets none of them.
        assertThat(ClassSpellGrants.grantsThrough("Bard", "College of Lore", null, 19)).isEmpty();
        assertThat(ClassSpellGrants.grantsThrough("Wizard", "Evoker", null, 20)).isEmpty();
    }

    @Test
    void aSpellGrantedTwice_isOwedOnce_underTheFirstFeature() {
        // Oath of Vengeance lists Hunter's Mark; nothing else of the paladin's grants it.
        List<SpellGrant> vengeance = ClassSpellGrants.grantsThrough("Paladin", "Oath of Vengeance", null, 20);
        assertThat(names(vengeance)).doesNotHaveDuplicates().contains("Hunter's Mark", "Misty Step", "Scrying");
        // A Draconic Sorcery sorcerer's Command (3rd) appears once across the whole list.
        assertThat(names(ClassSpellGrants.grantsThrough("Sorcerer", "Draconic Sorcery", null, 20)))
                .doesNotHaveDuplicates().contains("Command", "Summon Dragon");
    }

    @Test
    void worldOfWarcraftSubclasses_keepTheirSpellsOnTheAppsOwnTables() {
        // Their lists are in the feature text too, but the app shows them by specialization and level.
        assertThat(names(ClassSpellGrants.grantsThrough("Paladin", ClassProgression.OATH_OF_THE_HOLY, "Holy", 20)))
                .containsExactly("Divine Smite", "Find Steed");
        assertThat(ClassSpellGrants.grantsThrough("Cleric", ClassProgression.SHADOW_DOMAIN, "Shadow", 20)).isEmpty();
        assertThat(ClassSpellGrants.grantsThrough("Cleric", ClassProgression.DISCIPLINE_DOMAIN, "Discipline", 20)).isEmpty();
        // A World of Warcraft Evoker swaps Draconic Spells for Evoker Spells, so the Draconic list is not owed.
        assertThat(ClassSpellGrants.grantsThrough("Sorcerer", "Draconic Sorcery", "Devastation", 20)).isEmpty();
    }

    @Test
    void unknownInput_grantsNothing() {
        assertThat(ClassSpellGrants.grantsThrough(null, null, null, 20)).isEmpty();
        assertThat(ClassSpellGrants.grantsThrough("artificer", "Alchemist", null, 20)).isEmpty();
        assertThat(ClassSpellGrants.grantsThrough("Fighter", "Champion", null, 20)).isEmpty();
        assertThat(ClassSpellGrants.grantsThrough("Paladin", "Oath of Devotion", null, 0)).isEmpty();
    }

    /**
     * Every catalog subclass feature that says a spell is always prepared must either carry a
     * list this class reads, be named in its feature table, or be listed here as deliberately
     * not granted. A new subclass whose text names spells fails this test until it is wired.
     */
    @Test
    void everyAlwaysPreparedSentenceInTheCatalogIsAccountedFor() {
        // Spells the player chooses (a land type after each Long Rest, two wizard spells of their
        // own, an Arcana Domain cleric's four Wizard spells, the domain behind a Vestige Patron's
        // spells), so there is no fixed spell to hand over.
        Set<String> notGranted = Set.of("Circle of the Land Spells", "Signature Spells",
                SubclassChoices.MAGICAL_MASTERY, "Vestige Spells");
        List<String> unaccounted = new ArrayList<>();
        int lists = 0;
        for (String clazz : CLASSES) {
            for (String subclass : ClassProgression.subclassesFor(clazz)) {
                for (FeatureProgressionEntry feature : ClassFeatures.progression(clazz, subclass, null)) {
                    if (!feature.desc().toLowerCase().contains("always have")) continue;
                    boolean hasList = feature.subclass() && !ClassSpellGrants.spellListRows(feature.desc()).isEmpty();
                    if (hasList) lists++;
                    if (!hasList && !ClassSpellGrants.featureNames(clazz).contains(feature.name())
                            && !notGranted.contains(feature.name())) {
                        unaccounted.add(clazz + " / " + subclass + " / " + feature.name());
                    }
                }
            }
        }
        assertThat(unaccounted).isEmpty();
        // 6 cleric domains (the Arcana Domain among them), 2 druid circles, 4 oaths, 2 ranger,
        // 3 sorcerer, 4 warlock patrons.
        assertThat(lists).isEqualTo(21);
    }

    // --- Arcana Unleashed subclasses ---

    @Test
    void arcanaDomain_grantsEachRowOfItsListAtItsLevel() {
        assertThat(ClassSpellGrants.grantsThrough("Cleric", "Arcana Domain", null, 2)).isEmpty();
        assertThat(ClassSpellGrants.grantsAt("Cleric", "Arcana Domain", null, 3)).containsExactly(
                new SpellGrant("Detect Magic", "Arcana Domain Spells"),
                new SpellGrant("Magic Missile", "Arcana Domain Spells"),
                new SpellGrant("Magic Weapon", "Arcana Domain Spells"),
                new SpellGrant("Nystul's Magic Aura", "Arcana Domain Spells"));
        assertThat(ClassSpellGrants.grantsAt("Cleric", "Arcana Domain", null, 4)).isEmpty();
        assertThat(names(ClassSpellGrants.grantsAt("cleric", "arcana domain", null, 5)))
                .containsExactly("Counterspell", "Dispel Magic");
        assertThat(names(ClassSpellGrants.grantsAt("Cleric", "Arcana Domain", null, 7)))
                .containsExactly("Arcane Eye", "Leomund's Secret Chest");
        assertThat(names(ClassSpellGrants.grantsAt("Cleric", "Arcana Domain", null, 9)))
                .containsExactly("Bigby's Hand", "Teleportation Circle");
        // Nothing after the last row: Magical Mastery's spells are the player's picks.
        assertThat(names(ClassSpellGrants.grantsThrough("Cleric", "Arcana Domain", null, 20))).containsExactly(
                "Detect Magic", "Magic Missile", "Magic Weapon", "Nystul's Magic Aura",
                "Counterspell", "Dispel Magic", "Arcane Eye", "Leomund's Secret Chest",
                "Bigby's Hand", "Teleportation Circle");
        // A World of Warcraft Priest who takes the domain is granted the same list.
        assertThat(names(ClassSpellGrants.grantsAt("Cleric", "Arcana Domain", "Holy", 5)))
                .containsExactly("Counterspell", "Dispel Magic");
    }

    @Test
    void necromancerAndTransmuter_gainTheSpellsTheirFeaturesKeepPrepared() {
        assertThat(ClassSpellGrants.grantsAt("Wizard", "Necromancer", null, 3))
                .containsExactly(new SpellGrant("Find Familiar", "Necromancy Spellbook"));
        assertThat(ClassSpellGrants.grantsAt("Wizard", "Necromancer", null, 6))
                .containsExactly(new SpellGrant("Animate Dead", "Undead Thralls"));
        assertThat(names(ClassSpellGrants.grantsThrough("Wizard", "Necromancer", null, 20)))
                .containsExactly("Find Familiar", "Animate Dead");

        assertThat(ClassSpellGrants.grantsAt("Wizard", "Transmuter", null, 3))
                .containsExactly(new SpellGrant("Alter Self", "Wondrous Alteration"));
        assertThat(ClassSpellGrants.grantsAt("Wizard", "Transmuter", null, 10))
                .containsExactly(new SpellGrant("Polymorph", "Shape-Shifter"));
        assertThat(names(ClassSpellGrants.grantsThrough("wizard", "transmuter", null, 9)))
                .containsExactly("Alter Self");
    }

    @Test
    void theOtherArcanaUnleashedSubclasses_grantNoFixedSpell() {
        assertThat(ClassSpellGrants.grantsThrough("Wizard", "Conjurer", null, 20)).isEmpty();
        assertThat(ClassSpellGrants.grantsThrough("Wizard", "Enchanter", null, 20)).isEmpty();
        assertThat(ClassSpellGrants.grantsThrough("Fighter", "Arcane Archer", null, 20)).isEmpty();
        assertThat(ClassSpellGrants.grantsThrough("Monk", "Warrior of the Mystic Arts", null, 20)).isEmpty();
        // A Vestige Patron keeps the Warlock's own Contact Other Plane; its Vestige Spells depend
        // on the domain the player chose and are not granted here.
        assertThat(names(ClassSpellGrants.grantsThrough("Warlock", "Vestige Patron", null, 20)))
                .containsExactly("Contact Other Plane");
    }
}
