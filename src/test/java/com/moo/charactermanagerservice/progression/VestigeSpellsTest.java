package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.SpellGrant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Vestige Patron's Vestige Spells: the chosen Cleric domain's rows, at the Warlock levels the
 * domain lists them at, each granted by "Vestige Spells".
 */
class VestigeSpellsTest {

    private static List<String> names(String domain, int level) {
        return VestigeSpells.grantsThrough("Warlock", "Vestige Patron", domain, level).stream()
                .map(SpellGrant::name).toList();
    }

    /** The spells a domain adds exactly on reaching a level. */
    private static List<String> arriving(String domain, int level) {
        List<String> before = names(domain, level - 1);
        return names(domain, level).stream().filter(n -> !before.contains(n)).toList();
    }

    @Test
    void eachDomain_handsOverItsRowsAtWarlockThreeFiveSevenAndNine() {
        assertThat(arriving("Life", 3)).containsExactly("Aid", "Bless", "Cure Wounds", "Lesser Restoration");
        assertThat(arriving("Life", 5)).containsExactly("Mass Healing Word", "Revivify");
        assertThat(arriving("Life", 7)).containsExactly("Aura of Life", "Death Ward");
        assertThat(arriving("Life", 9)).containsExactly("Greater Restoration", "Mass Cure Wounds");

        assertThat(arriving("Light", 3))
                .containsExactly("Burning Hands", "Faerie Fire", "Scorching Ray", "See Invisibility");
        assertThat(arriving("Light", 5)).containsExactly("Daylight", "Fireball");
        assertThat(arriving("Light", 7)).containsExactly("Arcane Eye", "Wall of Fire");
        assertThat(arriving("Light", 9)).containsExactly("Flame Strike", "Scrying");

        assertThat(arriving("Trickery", 3))
                .containsExactly("Charm Person", "Disguise Self", "Invisibility", "Pass without Trace");
        assertThat(arriving("Trickery", 5)).containsExactly("Hypnotic Pattern", "Nondetection");
        assertThat(arriving("Trickery", 7)).containsExactly("Confusion", "Dimension Door");
        assertThat(arriving("Trickery", 9)).containsExactly("Dominate Person", "Modify Memory");

        // The book's own example: a War domain Vestige Patron at Warlock 3.
        assertThat(arriving("War", 3))
                .containsExactly("Guiding Bolt", "Magic Weapon", "Shield of Faith", "Spiritual Weapon");
        assertThat(arriving("War", 5)).containsExactly("Crusader's Mantle", "Spirit Guardians");
        assertThat(arriving("War", 7)).containsExactly("Fire Shield", "Freedom of Movement");
        assertThat(arriving("War", 9)).containsExactly("Hold Monster", "Steel Wind Strike");
    }

    @Test
    void nothingArrivesBetweenTheRows_orAfterTheLast() {
        for (String domain : SubclassChoices.VESTIGE_DOMAINS) {
            assertThat(names(domain, 2)).isEmpty();
            for (int level : List.of(4, 6, 8, 10, 15, 20)) {
                assertThat(arriving(domain, level)).as(domain + " at " + level).isEmpty();
            }
            assertThat(names(domain, 20)).hasSize(10);
        }
    }

    @Test
    void everySpell_isGrantedByVestigeSpells_whateverTheDomain() {
        for (String domain : SubclassChoices.VESTIGE_DOMAINS) {
            assertThat(VestigeSpells.grantsThrough("Warlock", "Vestige Patron", domain, 20))
                    .extracting(SpellGrant::grantedBy).containsOnly("Vestige Spells");
        }
        assertThat(VestigeSpells.FEATURE).isEqualTo("Vestige Spells");
    }

    @Test
    void theRows_areTheClericDomainsOwn() {
        for (String domain : SubclassChoices.VESTIGE_DOMAINS) {
            List<String> cleric = ClassSpellGrants.grantsThrough("Cleric", domain + " Domain", null, 20).stream()
                    .map(SpellGrant::name).toList();
            assertThat(names(domain, 20)).isEqualTo(cleric);
        }
    }

    @Test
    void theDomainAndTheSubclass_areMatchedWithoutRegardToCase() {
        assertThat(VestigeSpells.grantsThrough("warlock", " vestige patron ", " war ", 3))
                .extracting(SpellGrant::name)
                .containsExactly("Guiding Bolt", "Magic Weapon", "Shield of Faith", "Spiritual Weapon");
    }

    @Test
    void nobodyElse_andNoOtherDomain_getsAnything() {
        assertThat(VestigeSpells.grantsThrough("Warlock", "Fiend Patron", "Life", 20)).isEmpty();
        assertThat(VestigeSpells.grantsThrough("Warlock", null, "Life", 20)).isEmpty();
        assertThat(VestigeSpells.grantsThrough("Cleric", "Vestige Patron", "Life", 20)).isEmpty();
        assertThat(VestigeSpells.grantsThrough("Cleric", "Life Domain", "Life", 20)).isEmpty();
        assertThat(VestigeSpells.grantsThrough(null, null, "Life", 20)).isEmpty();
        // A domain that is not chosen, or not one of the four (Twilight and Arcana are Cleric domains).
        assertThat(VestigeSpells.grantsThrough("Warlock", "Vestige Patron", null, 20)).isEmpty();
        assertThat(VestigeSpells.grantsThrough("Warlock", "Vestige Patron", " ", 20)).isEmpty();
        assertThat(VestigeSpells.grantsThrough("Warlock", "Vestige Patron", "Twilight", 20)).isEmpty();
        assertThat(VestigeSpells.grantsThrough("Warlock", "Vestige Patron", "Arcana", 20)).isEmpty();
    }

    @Test
    void byDomain_listsTheFourInOrder_fromWarlockThree() {
        Map<String, List<SpellGrant>> atFive = VestigeSpells.grantsThroughByDomain("Warlock", "Vestige Patron", 5);
        assertThat(atFive.keySet()).containsExactly("Life", "Light", "Trickery", "War");
        assertThat(atFive.get("War")).extracting(SpellGrant::name).containsExactly(
                "Guiding Bolt", "Magic Weapon", "Shield of Faith", "Spiritual Weapon",
                "Crusader's Mantle", "Spirit Guardians");

        assertThat(VestigeSpells.grantsThroughByDomain("Warlock", "Vestige Patron", 2)).isEmpty();
        assertThat(VestigeSpells.grantsThroughByDomain("Warlock", "Fiend Patron", 9)).isEmpty();
        assertThat(VestigeSpells.grantsThroughByDomain("Wizard", "Evoker", 9)).isEmpty();
    }
}
