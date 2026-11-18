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
}
