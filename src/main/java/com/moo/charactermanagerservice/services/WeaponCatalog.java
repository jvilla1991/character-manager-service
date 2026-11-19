package com.moo.charactermanagerservice.services;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Melee or ranged, for the weapons the shop catalog knows (the SRD 2024 weapon
 * table seeded by {@code V11__seed_srd_weapons.sql}, whose {@code details}
 * carry {@code "range":"melee"|"ranged"}).
 *
 * <p>A weapon on a character sheet is a snapshot with no range of its own, and
 * the rules that need the answer ({@link LongRestRules}) are pure and run where
 * no repository is at hand, so the seed's answer is repeated here as a table.
 * The seed stays the source of truth: {@code WeaponCatalogSeedTest} fails if
 * this table and the seed ever disagree. The client carries the same table
 * (utils/weapon-catalog.ts).
 *
 * <p>Lookups go by catalog key first (stamped on every bought or granted item),
 * then by exact name, ignoring case — which is how starting equipment and the
 * older free-text weapon list match. Anything else is not in the catalog and
 * the caller falls back to reading the weapon's name and properties.
 */
final class WeaponCatalog {

    private WeaponCatalog() {}

    /** catalog key → true when ranged, in seed order. */
    static final Map<String, Boolean> RANGED_BY_KEY = new LinkedHashMap<>();
    /** lower-cased display name → true when ranged. */
    private static final Map<String, Boolean> RANGED_BY_NAME = new LinkedHashMap<>();

    private static void add(String key, String name, boolean ranged) {
        RANGED_BY_KEY.put(key, ranged);
        RANGED_BY_NAME.put(name.toLowerCase(Locale.ROOT), ranged);
    }

    static {
        // Simple melee
        add("club", "Club", false);
        add("dagger", "Dagger", false);
        add("greatclub", "Greatclub", false);
        add("handaxe", "Handaxe", false);
        add("javelin", "Javelin", false);
        add("light-hammer", "Light Hammer", false);
        add("mace", "Mace", false);
        add("quarterstaff", "Quarterstaff", false);
        add("sickle", "Sickle", false);
        add("spear", "Spear", false);
        // Simple ranged
        add("dart", "Dart", true);
        add("light-crossbow", "Light Crossbow", true);
        add("shortbow", "Shortbow", true);
        add("sling", "Sling", true);
        // Martial melee
        add("battleaxe", "Battleaxe", false);
        add("flail", "Flail", false);
        add("glaive", "Glaive", false);
        add("greataxe", "Greataxe", false);
        add("greatsword", "Greatsword", false);
        add("halberd", "Halberd", false);
        add("lance", "Lance", false);
        add("longsword", "Longsword", false);
        add("maul", "Maul", false);
        add("morningstar", "Morningstar", false);
        add("pike", "Pike", false);
        add("rapier", "Rapier", false);
        add("scimitar", "Scimitar", false);
        add("shortsword", "Shortsword", false);
        add("trident", "Trident", false);
        add("war-pick", "War Pick", false);
        add("warhammer", "Warhammer", false);
        add("whip", "Whip", false);
        // Martial ranged
        add("blowgun", "Blowgun", true);
        add("hand-crossbow", "Hand Crossbow", true);
        add("heavy-crossbow", "Heavy Crossbow", true);
        add("longbow", "Longbow", true);
        add("net", "Net", true);
    }

    /**
     * Whether a catalog weapon is ranged; empty when neither the key nor the
     * name is a catalog weapon.
     */
    static Optional<Boolean> ranged(String catalogKey, String name) {
        if (catalogKey != null) {
            Boolean byKey = RANGED_BY_KEY.get(catalogKey.trim().toLowerCase(Locale.ROOT));
            if (byKey != null) {
                return Optional.of(byKey);
            }
        }
        if (name != null) {
            Boolean byName = RANGED_BY_NAME.get(name.trim().toLowerCase(Locale.ROOT));
            if (byName != null) {
                return Optional.of(byName);
            }
        }
        return Optional.empty();
    }
}
