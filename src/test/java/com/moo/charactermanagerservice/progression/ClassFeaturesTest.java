package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;
import com.moo.charactermanagerservice.dto.FeatureProgressionEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the class-feature catalog: the level 2–20 roadmap the character sheet reads, and the
 * guarantee that every subclass a character can pick actually has features behind it.
 */
class ClassFeaturesTest {

    /** The levels at which each class's subclass grants features (2024 Player's Handbook). */
    private static final Map<String, Set<Integer>> SUBCLASS_LEVELS = Map.ofEntries(
            Map.entry("barbarian", Set.of(3, 6, 10, 14)),
            Map.entry("bard", Set.of(3, 6, 14)),
            Map.entry("cleric", Set.of(3, 6, 17)),
            Map.entry("druid", Set.of(3, 6, 10, 14)),
            Map.entry("fighter", Set.of(3, 7, 10, 15, 18)),
            Map.entry("monk", Set.of(3, 6, 11, 17)),
            Map.entry("paladin", Set.of(3, 7, 15, 20)),
            Map.entry("ranger", Set.of(3, 7, 11, 15)),
            Map.entry("rogue", Set.of(3, 9, 13, 17)),
            Map.entry("sorcerer", Set.of(3, 6, 14, 18)),
            Map.entry("warlock", Set.of(3, 6, 10, 14)),
            Map.entry("wizard", Set.of(3, 6, 10, 14))
    );

    private static List<FeatureProgressionEntry> subclassEntries(String clazz, String subclass) {
        return ClassFeatures.progression(clazz, subclass, null).stream()
                .filter(FeatureProgressionEntry::subclass)
                .toList();
    }

    @Test
    void everyCatalogSubclass_hasFeaturesAtEachOfItsClassSubclassLevels() {
        SUBCLASS_LEVELS.forEach((clazz, levels) -> {
            List<String> subclasses = ClassProgression.subclassesFor(clazz);
            assertThat(subclasses).as("%s has catalog subclasses", clazz).isNotEmpty();
            for (String subclass : subclasses) {
                Set<Integer> covered = subclassEntries(clazz, subclass).stream()
                        .map(FeatureProgressionEntry::level)
                        .collect(Collectors.toSet());
                assertThat(covered).as("%s / %s subclass feature levels", clazz, subclass).isEqualTo(levels);
            }
        });
    }

    @Test
    void wowPriestDomains_haveFeaturesAtEachClericSubclassLevel() {
        for (String domain : List.of(ClassProgression.DISCIPLINE_DOMAIN, ClassProgression.SHADOW_DOMAIN)) {
            List<FeatureProgressionEntry> entries = ClassFeatures.progression("Cleric", domain, "Shadow").stream()
                    .filter(FeatureProgressionEntry::subclass)
                    .toList();
            assertThat(entries.stream().map(FeatureProgressionEntry::level).collect(Collectors.toSet()))
                    .as("%s subclass feature levels", domain)
                    .isEqualTo(SUBCLASS_LEVELS.get("cleric"));
            assertThat(entries).allSatisfy(e -> {
                assertThat(e.name()).isNotBlank();
                assertThat(e.desc()).isNotBlank();
            });
        }
        assertThat(ClassProgression.subclassesFor("cleric", "Holy"))
                .containsAll(ClassProgression.subclassesFor("cleric"))
                .contains(ClassProgression.DISCIPLINE_DOMAIN, ClassProgression.SHADOW_DOMAIN);
        assertThat(ClassProgression.subclassesFor("cleric", null))
                .doesNotContain(ClassProgression.DISCIPLINE_DOMAIN, ClassProgression.SHADOW_DOMAIN);
    }

    // --- Arcana Unleashed subclasses ---

    private static List<String> namesAt(String clazz, String subclass, int level) {
        return subclassEntries(clazz, subclass).stream()
                .filter(e -> e.level() == level).map(FeatureProgressionEntry::name).toList();
    }

    @Test
    void arcanaDomain_featuresByLevel() {
        assertThat(namesAt("Cleric", "Arcana Domain", 3))
                .containsExactly("Arcana Domain Spells", "Modify Magic", "Student of Arcana");
        assertThat(namesAt("Cleric", "Arcana Domain", 6)).containsExactly("Dispelling Recovery");
        assertThat(namesAt("Cleric", "Arcana Domain", 17)).containsExactly("Magical Mastery");
        assertThat(subclassEntries("Cleric", "Arcana Domain")).hasSize(5);
    }

    @Test
    void arcaneArcher_featuresByLevel_withItsOptionsAsReadableText() {
        assertThat(namesAt("Fighter", "Arcane Archer", 3))
                .containsExactly("Arcane Archer Lore", "Arcane Shot", "Arcane Shot Options");
        assertThat(namesAt("Fighter", "Arcane Archer", 7)).containsExactly("Curving Shot", "Magical Ammunition");
        assertThat(namesAt("Fighter", "Arcane Archer", 10)).containsExactly("Ever-Ready Shot");
        assertThat(namesAt("Fighter", "Arcane Archer", 15)).containsExactly("Indomitable Teleport");
        assertThat(namesAt("Fighter", "Arcane Archer", 18)).containsExactly("Masterful Shots");

        String options = subclassEntries("Fighter", "Arcane Archer").stream()
                .filter(e -> e.name().equals("Arcane Shot Options")).findFirst().orElseThrow().desc();
        // Every option the player can choose is described, under the name it is chosen by.
        for (String option : SubclassChoices.ARCANE_SHOT_OPTIONS) {
            assertThat(options).as(option).contains(option + ":");
        }
    }

    @Test
    void warriorOfTheMysticArts_featuresByLevel() {
        assertThat(namesAt("Monk", "Warrior of the Mystic Arts", 3)).containsExactly("Spellcasting");
        assertThat(namesAt("Monk", "Warrior of the Mystic Arts", 6))
                .containsExactly("Mystic Fighting Style", "Mystic Focus");
        assertThat(namesAt("Monk", "Warrior of the Mystic Arts", 11)).containsExactly("Focused Strike");
        assertThat(namesAt("Monk", "Warrior of the Mystic Arts", 17))
                .containsExactly("Improved Mystic Fighting Style");
    }

    @Test
    void vestigePatron_featuresArriveFromWarlockThree_withTheCompanionAsReadableText() {
        // The patron is picked at Warlock 1 in the create wizard; nothing arrives before level 3.
        assertThat(namesAt("Warlock", "Vestige Patron", 2)).isEmpty();
        assertThat(namesAt("Warlock", "Vestige Patron", 3))
                .containsExactly("Vestige Companion", "Vestige Companion Stat Block", "Vestige Spells");
        assertThat(namesAt("Warlock", "Vestige Patron", 6)).containsExactly("Vestige Power");
        assertThat(namesAt("Warlock", "Vestige Patron", 10)).containsExactly("Vestige Recovery");
        assertThat(namesAt("Warlock", "Vestige Patron", 14)).containsExactly("Semblance of Life");

        String statBlock = subclassEntries("Warlock", "Vestige Patron").stream()
                .filter(e -> e.name().equals("Vestige Companion Stat Block")).findFirst().orElseThrow().desc();
        assertThat(statBlock).contains("AC 13", "Fly 30 ft.", "Vestige's Strike", "Divine Power",
                "Healing Touch", "Fiendish Swap", "Cursed Invocation");
    }

    @Test
    void theFourWizardSchools_featuresByLevel() {
        assertThat(namesAt("Wizard", "Conjurer", 3)).containsExactly("Benign Transposition", "Conjuration Savant");
        assertThat(namesAt("Wizard", "Conjurer", 6)).containsExactly("Distant Transposition", "Durable Summons");
        assertThat(namesAt("Wizard", "Conjurer", 10)).containsExactly("Focused Conjuration");
        assertThat(namesAt("Wizard", "Conjurer", 14)).containsExactly("Splintered Summons");

        assertThat(namesAt("Wizard", "Enchanter", 3))
                .containsExactly("Enchanting Conversationalist", "Enchantment Savant", "Hypnotic Presence");
        assertThat(namesAt("Wizard", "Enchanter", 6)).containsExactly("Split Enchantment");
        assertThat(namesAt("Wizard", "Enchanter", 10)).containsExactly("Instinctive Charm");
        assertThat(namesAt("Wizard", "Enchanter", 14)).containsExactly("Alter Memories");

        assertThat(namesAt("Wizard", "Necromancer", 3)).containsExactly("Necromancy Savant", "Necromancy Spellbook");
        assertThat(namesAt("Wizard", "Necromancer", 6)).containsExactly("Grave Power", "Undead Thralls");
        assertThat(namesAt("Wizard", "Necromancer", 10)).containsExactly("Harvest Undead");
        assertThat(namesAt("Wizard", "Necromancer", 14)).containsExactly("Death's Master");

        assertThat(namesAt("Wizard", "Transmuter", 3))
                .containsExactly("Transmutation Savant", "Transmuter's Stone", "Wondrous Alteration");
        assertThat(namesAt("Wizard", "Transmuter", 6)).containsExactly("Empowered Transmutation");
        assertThat(namesAt("Wizard", "Transmuter", 10)).containsExactly("Potent Stone", "Shape-Shifter");
        assertThat(namesAt("Wizard", "Transmuter", 14)).containsExactly("Master Transmuter");
    }

    @Test
    void everyCounterOfAnArcanaUnleashedSubclass_belongsToAFeatureTheSubclassHas() {
        String[][] subclasses = {
                {"Cleric", "Arcana Domain"}, {"Fighter", "Arcane Archer"}, {"Monk", "Warrior of the Mystic Arts"},
                {"Warlock", "Vestige Patron"}, {"Wizard", "Conjurer"}, {"Wizard", "Enchanter"},
                {"Wizard", "Necromancer"}, {"Wizard", "Transmuter"}};
        int counters = 0;
        for (String[] c : subclasses) {
            Set<String> ownFeatures = subclassEntries(c[0], c[1]).stream()
                    .map(FeatureProgressionEntry::name).collect(Collectors.toSet());
            Set<String> classKeys = ClassResources.forCharacter(c[0], null, 20, 20, 20, 20, List.of()).stream()
                    .map(ClassResources.Resource::key).collect(Collectors.toSet());
            for (ClassResources.Resource r : ClassResources.forCharacter(c[0], c[1], 20, 20, 20, 20, List.of())) {
                if (classKeys.contains(r.key())) continue; // the class's own counter
                assertThat(ownFeatures).as("%s / %s counter %s", c[0], c[1], r.key()).contains(r.feature());
                counters++;
            }
        }
        // 1 cleric, 2 fighter, 0 monk, 3 warlock, 2 + 3 + 2 + 5 wizard.
        assertThat(counters).isEqualTo(18);
    }

    @Test
    void everyFeature_hasANameAndADescription() {
        SUBCLASS_LEVELS.keySet().forEach(clazz -> {
            List<String> subclasses = new ArrayList<>(ClassProgression.subclassesFor(clazz));
            subclasses.add(null); // the class's own features, with no subclass
            for (String subclass : subclasses) {
                assertThat(ClassFeatures.progression(clazz, subclass, null))
                        .as("%s / %s", clazz, subclass)
                        .isNotEmpty()
                        .allSatisfy(e -> {
                            assertThat(e.name()).isNotBlank();
                            assertThat(e.desc()).isNotBlank();
                        });
            }
        });
    }

    @Test
    void progression_runsFromLevel2To20_inLevelOrder() {
        List<FeatureProgressionEntry> all = ClassFeatures.progression("Fighter", "Champion", null);
        List<Integer> levels = all.stream().map(FeatureProgressionEntry::level).toList();
        assertThat(levels).isSorted();
        assertThat(levels).allSatisfy(l -> assertThat(l).isBetween(2, 20));
        assertThat(levels).contains(2, 20);
    }

    @Test
    void progression_withoutASubclass_hasOnlyTheClassFeatures() {
        List<FeatureProgressionEntry> generic = ClassFeatures.progression("Fighter", null, null);
        assertThat(generic).isNotEmpty().noneMatch(FeatureProgressionEntry::subclass);

        List<FeatureProgressionEntry> champion = ClassFeatures.progression("Fighter", "Champion", null);
        assertThat(champion).filteredOn(e -> !e.subclass()).isEqualTo(generic);
        assertThat(champion).filteredOn(FeatureProgressionEntry::subclass)
                .extracting(FeatureProgressionEntry::name)
                .contains("Improved Critical");
    }

    @Test
    void progression_matchesWhatLevelUpGrants_levelByLevel() {
        // The sheet's roadmap and the level-up grant read the same catalog; they must agree,
        // including for a hero subclass that replaces generic features.
        for (String[] c : new String[][] {
                {"Paladin", "Oath of Devotion"}, {"Paladin", ClassProgression.EBON_BLADE}, {"Wizard", "Evoker"}}) {
            List<FeatureProgressionEntry> roadmap = ClassFeatures.progression(c[0], c[1], null);
            for (int level = 2; level <= 20; level++) {
                final int lvl = level;
                List<FeatureGain> granted = ClassFeatures.featuresAt(c[0], c[1], null, level);
                assertThat(roadmap.stream().filter(e -> e.level() == lvl)
                        .map(e -> new FeatureGain(e.name(), e.desc())).toList())
                        .as("%s / %s at level %d", c[0], c[1], level)
                        .isEqualTo(granted);
            }
        }
    }

    @Test
    void progression_dropsGenericFeaturesAHeroSubclassReplaces() {
        assertThat(ClassFeatures.progression("Paladin", null, null))
                .extracting(FeatureProgressionEntry::name).contains("Divine Smite");
        assertThat(ClassFeatures.progression("Paladin", ClassProgression.EBON_BLADE, null))
                .extracting(FeatureProgressionEntry::name).doesNotContain("Divine Smite");
    }

    @Test
    void progression_isEmpty_forAnUnknownOrMissingClass() {
        assertThat(ClassFeatures.progression("Artificer", null, null)).isEmpty();
        assertThat(ClassFeatures.progression(null, null, null)).isEmpty();
    }

    @Test
    void progression_ignoresAnUnknownSubclass() {
        assertThat(ClassFeatures.progression("Fighter", "Rune Knight", null))
                .isEqualTo(ClassFeatures.progression("Fighter", null, null));
    }

    @Test
    void wowMonkMysticBrews_hasFeaturesAtEachMonkSubclassLevel() {
        List<FeatureProgressionEntry> entries = ClassFeatures
                .progression("Monk", ClassProgression.WARRIOR_OF_MYSTIC_BREWS, "Brewmaster").stream()
                .filter(FeatureProgressionEntry::subclass)
                .toList();
        assertThat(entries.stream().map(FeatureProgressionEntry::level).collect(Collectors.toSet()))
                .isEqualTo(SUBCLASS_LEVELS.get("monk"));
        assertThat(entries.stream().map(FeatureProgressionEntry::name).toList()).containsExactly(
                "Bonus Proficiencies", "Drunken Technique", "Tipsy Sway", "Mystic Brew", "Wizened Brewer",
                "Master Brewer", "Intoxicated Frenzy");
        assertThat(entries).allSatisfy(e -> {
            assertThat(e.name()).isNotBlank();
            assertThat(e.desc()).isNotBlank();
        });
        assertThat(ClassProgression.subclassesFor("monk", "Windwalker"))
                .containsAll(ClassProgression.subclassesFor("monk"))
                .contains(ClassProgression.WARRIOR_OF_MYSTIC_BREWS);
        assertThat(ClassProgression.subclassesFor("monk", null))
                .doesNotContain(ClassProgression.WARRIOR_OF_MYSTIC_BREWS);
    }
}
