package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.SpellGrant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Vestige Patron's Vestige Spells (Arcana Unleashed): the warlock picks one Cleric domain
 * (Life, Light, Trickery or War: {@link SubclassChoices#VESTIGE_DOMAINS}) and always has that
 * domain's spells prepared, each row from the Warlock level equal to the Cleric level the domain
 * lists it at (3, 5, 7 and 9).
 *
 * <p>The rows are not written here: they are read from the domain's own
 * {@code "<Domain> Domain Spells"} feature through {@link ClassSpellGrants}, so a Vestige Patron
 * and a cleric of the same domain can never be handed different lists. Only the name of the
 * granting feature differs: a Vestige Patron's are {@value #FEATURE} spells.
 *
 * <p>Kept apart from {@link ClassSpellGrants} because these grants need something that table
 * never looks at: a choice stored on the character ({@code vestigeDomain}).
 */
public final class VestigeSpells {

    private VestigeSpells() {}

    /** The feature that grants them: the {@code grantedBy} of every Vestige Spell. */
    public static final String FEATURE = "Vestige Spells";

    /** The option's own spelling of a domain name (case and outer spaces ignored), or null. */
    private static String domainOf(String name) {
        if (name == null) return null;
        String wanted = name.trim();
        return SubclassChoices.VESTIGE_DOMAINS.stream()
                .filter(d -> d.equalsIgnoreCase(wanted)).findFirst().orElse(null);
    }

    private static boolean isVestigePatron(String clazz, String subclass) {
        return SubclassChoices.VESTIGE_PATRON.equals(SubclassChoices.subclassWithChoices(clazz, subclass));
    }

    /**
     * Every Vestige Spell a Vestige Patron of {@code level} with this domain should have, earliest
     * row first. Empty for any other class or subclass, for a domain that is unset or not one of
     * the four, and below Warlock 3.
     */
    public static List<SpellGrant> grantsThrough(String clazz, String subclass, String domain, int level) {
        String chosen = domainOf(domain);
        if (chosen == null || !isVestigePatron(clazz, subclass)) return List.of();
        String domainSpells = chosen + " Domain Spells";
        return ClassSpellGrants.grantsThrough("cleric", chosen + " Domain", null, level).stream()
                .filter(g -> domainSpells.equals(g.grantedBy()))
                .map(g -> new SpellGrant(g.name(), FEATURE))
                .toList();
    }

    /**
     * {@link #grantsThrough} for each of the four domains, in {@link SubclassChoices#VESTIGE_DOMAINS}
     * order; a domain that gives nothing at the level is left out. Empty for anyone but a Vestige
     * Patron of Warlock 3 or higher.
     */
    public static Map<String, List<SpellGrant>> grantsThroughByDomain(String clazz, String subclass, int level) {
        Map<String, List<SpellGrant>> byDomain = new LinkedHashMap<>();
        for (String domain : SubclassChoices.VESTIGE_DOMAINS) {
            List<SpellGrant> grants = grantsThrough(clazz, subclass, domain, level);
            if (!grants.isEmpty()) byDomain.put(domain, grants);
        }
        return byDomain;
    }
}
