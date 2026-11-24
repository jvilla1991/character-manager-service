package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.SpellGrant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spells a feat gives a character (the Arcana Unleashed book's feats), the feat counterpart of
 * {@link ClassSpellGrants}. Three kinds:
 * <ul>
 *   <li><b>Named spells</b> ({@link #NAMED}): a feat that names its spell — Arcane Artist's Minor
 *       Illusion, Familiar Friend's Find Familiar. The character has it from the moment it has
 *       the feat.</li>
 *   <li><b>School Adept tables</b> ({@link #SCHOOL_ADEPT}): one spell per spell slot level 1 to 5.
 *       A character has the rows for its highest slot level and lower
 *       ({@link ClassProgression#highestSpellSlotLevel}), so new rows arrive as it levels.</li>
 *   <li><b>Picks</b> ({@link #pickRule}): a feat that lets the player choose — Arcane Warrior's
 *       two Wizard cantrips, Magic Connoisseur's level 1 and level 2 spell, Boon of Magic School
 *       Mastery's two spells of one school. The level-up request carries them marked with the
 *       feat's name; only their number, spell level and school are checked here, because the
 *       spell lists live in the app.</li>
 * </ul>
 *
 * <p>Names only, as in {@link ClassSpellGrants}: the app sends the full spell with the level-up.
 * Every granted spell is saved always prepared with the feat as {@code grantedBy}. Feat names
 * are matched without regard to case or surrounding spaces.
 */
public final class FeatSpellGrants {

    private FeatSpellGrants() {}

    /** Feat -> the spells it names (a cantrip is simply known). */
    private static final Map<String, List<String>> NAMED = named();

    private static Map<String, List<String>> named() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("Arcane Artist", List.of("Minor Illusion"));
        m.put("Arcane Eloquence", List.of("Vicious Mockery"));
        m.put("Arcane Infiltrator", List.of("Friends"));
        m.put("Arcane Omens", List.of("Guidance"));
        m.put("Arcane Overload", List.of("Fire Bolt"));
        m.put("Arcane Safeguard", List.of("Resistance"));
        m.put(FeatCatalog.FAMILIAR_FRIEND, List.of("Find Familiar"));
        m.put("Warlike Familiar", List.of("Battle Familiar"));
        return m;
    }

    /** School Adept feat -> its spells for spell slot levels 1, 2, 3, 4 and 5, in that order. */
    private static final Map<String, List<String>> SCHOOL_ADEPT = schoolAdept();

    private static Map<String, List<String>> schoolAdept() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("Abjuration Adept", List.of(
                "Shield", "Lesser Restoration", "Protection from Energy", "Banishment", "Mass Cure Wounds"));
        m.put("Conjuration Adept", List.of(
                "Entangle", "Misty Step", "Conjure Animals", "Dimension Door", "Conjure Elemental"));
        m.put("Divination Adept", List.of(
                "Detect Evil and Good", "Mind Spike", "Clairvoyance", "Divination", "Scrying"));
        m.put("Enchantment Adept", List.of(
                "Dissonant Whispers", "Enthrall", "Hold Person", "Dominate Beast", "Modify Memory"));
        m.put("Evocation Adept", List.of(
                "Chromatic Orb", "Shatter", "Fireball", "Vitriolic Sphere", "Wall of Force"));
        m.put("Illusion Adept", List.of(
                "Silent Image", "Phantasmal Force", "Major Image", "Hallucinatory Terrain", "Seeming"));
        m.put("Necromancy Adept", List.of(
                "Inflict Wounds", "Ray of Enfeeblement", "Vampiric Touch", "Blight", "Raise Dead"));
        m.put("Transmutation Adept", List.of(
                "Jump", "Spider Climb", "Slow", "Polymorph", "Animate Objects"));
        return m;
    }

    /** The table's own spelling of a feat name, or null when the table does not have it. */
    private static String keyIn(Map<String, List<String>> table, String feat) {
        if (feat == null) return null;
        String name = feat.trim();
        return table.keySet().stream().filter(k -> k.equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    /**
     * The spells one feat gives a character of this class and level: the spells it names, or a
     * School Adept's rows up to the character's highest spell slot level. Empty for every other
     * feat, and for a School Adept without spell slots.
     */
    public static List<SpellGrant> grantsFor(String feat, String clazz, String subclass, int level) {
        List<SpellGrant> grants = new ArrayList<>();
        String named = keyIn(NAMED, feat);
        if (named != null) {
            NAMED.get(named).forEach(spell -> grants.add(new SpellGrant(spell, named)));
        }
        String adept = keyIn(SCHOOL_ADEPT, feat);
        if (adept != null) {
            List<String> rows = SCHOOL_ADEPT.get(adept);
            int highest = Math.min(rows.size(), ClassProgression.highestSpellSlotLevel(clazz, subclass, level));
            for (int slotLevel = 1; slotLevel <= highest; slotLevel++) {
                grants.add(new SpellGrant(rows.get(slotLevel - 1), adept));
            }
        }
        return grants;
    }

    /**
     * Every spell the character's feats give it at this level, in the order the feats are held and
     * each spell once (the first feat to grant it names it). A level-up offers the ones the
     * character still lacks, so a character that is owed a spell catches up.
     */
    public static List<SpellGrant> grantsThrough(Collection<String> feats, String clazz, String subclass,
                                                 int level) {
        Map<String, SpellGrant> byName = new LinkedHashMap<>();
        if (feats == null) return List.of();
        for (String feat : feats) {
            for (SpellGrant grant : grantsFor(feat, clazz, subclass, level)) {
                byName.putIfAbsent(grant.name().toLowerCase(), grant);
            }
        }
        return List.copyOf(byName.values());
    }

    /** The spells a School Adept feat lists, by spell slot level 1 to 5 (for the tests). */
    static List<String> schoolAdeptSpells(String feat) {
        String adept = keyIn(SCHOOL_ADEPT, feat);
        return adept == null ? List.of() : SCHOOL_ADEPT.get(adept);
    }

    // ── Spells the player picks ──────────────────────────────────────────────────

    /** One pick: a spell whose level lies in {@code minLevel..maxLevel} (0 = a cantrip). */
    public record Pick(int minLevel, int maxLevel) {}

    /**
     * What a feat lets the player choose when it is taken.
     *
     * @param feat   the feat's name, as offered
     * @param picks  one entry per spell to choose
     * @param school the school every pick must belong to, or null for any school
     * @param needs  the picks in words, for a message: {@code "two cantrips"}
     */
    public record PickRule(String feat, List<Pick> picks, String school, String needs) {

        /** True when the spells' levels fill every pick exactly, one spell per pick. */
        public boolean satisfiedBy(List<Integer> spellLevels) {
            if (spellLevels.size() != picks.size()) return false;
            List<Integer> left = new ArrayList<>(spellLevels);
            left.sort(Comparator.naturalOrder());
            // Narrowest pick first, each taking the lowest spell that fits it.
            List<Pick> ordered = new ArrayList<>(picks);
            ordered.sort(Comparator.comparingInt(p -> p.maxLevel() - p.minLevel()));
            for (Pick pick : ordered) {
                Integer match = left.stream()
                        .filter(lvl -> lvl >= pick.minLevel() && lvl <= pick.maxLevel())
                        .findFirst().orElse(null);
                if (match == null) return false;
                left.remove(match);
            }
            return true;
        }
    }

    /** Highest spell level Boon of Magic School Mastery's free-choice spell may have. */
    public static final int SIGNATURE_ARCANUM_MAX_LEVEL = 7;

    /** The picks a feat asks for, by its name as offered; empty for a feat without any. */
    public static Optional<PickRule> pickRule(String feat) {
        if (feat == null) return Optional.empty();
        String name = feat.trim();
        if (name.equalsIgnoreCase(FeatCatalog.ARCANE_WARRIOR)) {
            return Optional.of(new PickRule(FeatCatalog.ARCANE_WARRIOR,
                    List.of(new Pick(0, 0), new Pick(0, 0)), null, "two cantrips"));
        }
        if (name.equalsIgnoreCase(FeatCatalog.MAGIC_CONNOISSEUR)) {
            return Optional.of(new PickRule(FeatCatalog.MAGIC_CONNOISSEUR,
                    List.of(new Pick(1, 1), new Pick(2, 2)), null, "one level 1 spell and one level 2 spell"));
        }
        for (String school : FeatCatalog.SCHOOLS_OF_MAGIC) {
            String mastery = FeatCatalog.schoolMasteryFeat(school);
            if (name.equalsIgnoreCase(mastery)) {
                return Optional.of(new PickRule(mastery,
                        List.of(new Pick(1, 1), new Pick(1, SIGNATURE_ARCANUM_MAX_LEVEL)), school,
                        "one level 1 " + school + " spell and one " + school + " spell of level "
                                + SIGNATURE_ARCANUM_MAX_LEVEL + " or lower"));
            }
        }
        return Optional.empty();
    }
}
