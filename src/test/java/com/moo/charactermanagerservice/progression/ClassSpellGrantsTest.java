package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.SpellGrant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClassSpellGrantsTest {

    @Test
    void paladin_gainsDivineSmiteAtTwo_andFindSteedAtFive() {
        assertThat(ClassSpellGrants.grantsAt("Paladin", null, null, 2))
                .containsExactly(new SpellGrant("Divine Smite", "Divine Smite"));
        assertThat(ClassSpellGrants.grantsAt(" paladin ", "Oath of Devotion", null, 5))
                .containsExactly(new SpellGrant("Find Steed", "Faithful Steed"));
        // A World of Warcraft Paladin is an ordinary paladin plus a specialization.
        assertThat(ClassSpellGrants.grantsAt("Paladin", "Oath of the Holy", "Holy", 5))
                .containsExactly(new SpellGrant("Find Steed", "Faithful Steed"));
    }

    @Test
    void deathKnight_losesDivineSmiteWithTheFeature_butKeepsFindSteed() {
        // Runic Smite replaces Divine Smite at Paladin 2; its Rune Strike is on the hero-class table.
        assertThat(ClassSpellGrants.grantsAt("Paladin", ClassProgression.EBON_BLADE, "Frost", 2)).isEmpty();
        // The Ebon Blade's Faithful Steed still keeps Find Steed prepared.
        assertThat(ClassSpellGrants.grantsAt("Paladin", ClassProgression.EBON_BLADE, "Frost", 5))
                .containsExactly(new SpellGrant("Find Steed", "Faithful Steed"));
    }

    @Test
    void warlockAndBard_gainTheirFeatureSpells() {
        assertThat(ClassSpellGrants.grantsAt("Warlock", "Fiend Patron", null, 9))
                .containsExactly(new SpellGrant("Contact Other Plane", "Contact Patron"));
        assertThat(ClassSpellGrants.grantsAt("Bard", "College of Lore", null, 20))
                .containsExactly(new SpellGrant("Power Word Heal", "Words of Creation"),
                        new SpellGrant("Power Word Kill", "Words of Creation"));
    }

    @Test
    void everyOtherClassAndLevel_gainsNothing() {
        int total = 0;
        for (String clazz : List.of("barbarian", "bard", "cleric", "druid", "fighter", "monk", "paladin",
                "ranger", "rogue", "sorcerer", "warlock", "wizard")) {
            for (int level = 2; level <= ClassProgression.MAX_LEVEL; level++) {
                total += ClassSpellGrants.grantsAt(clazz, null, null, level).size();
            }
        }
        // Divine Smite, Find Steed, Contact Other Plane, Power Word Heal, Power Word Kill.
        assertThat(total).isEqualTo(5);
        assertThat(ClassSpellGrants.allSpellNames()).hasSize(5);
        assertThat(ClassSpellGrants.grantsAt(null, null, null, 5)).isEmpty();
        assertThat(ClassSpellGrants.grantsAt("artificer", null, null, 5)).isEmpty();
    }
}
