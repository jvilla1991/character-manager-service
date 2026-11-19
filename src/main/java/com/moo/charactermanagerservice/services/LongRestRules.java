package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.LongRestChoices;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.progression.ClassProgression;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The choices a hero makes as a long rest ends (2024 PHB plus the World of
 * Warcraft tabletop module): which spells are prepared, and which weapon a
 * Death Knight's rune is inscribed on. Pure rules — the session service decides
 * when they are validated (at Ready) and applied (when the rest finishes).
 *
 * <p>The prepared-spell cap and the meaning of "prepared" deliberately mirror
 * the client's {@code utils/spell-preparation.ts}, which is what the sheet has
 * always enforced: the cap is the class table's Prepared Spells column, only
 * leveled spells on the hero's own list count, and a spell with no
 * {@code prepared} flag (saved before the flag existed) counts as prepared.
 */
final class LongRestRules {

    private LongRestRules() {}

    private static final PcJsonColumns JSON = new PcJsonColumns(new ObjectMapper());

    /** How far a class may change its prepared spells when a long rest ends. */
    enum SpellChange {
        /** Cleric, Druid, Wizard: any number. */
        ANY,
        /** Paladin, Ranger (and the hero classes stored on them): one spell. */
        ONE,
        /** Everyone else: none at a long rest. */
        NONE
    }

    static SpellChange spellChangeFor(String clazz) {
        return switch (key(clazz)) {
            case "cleric", "druid", "wizard" -> SpellChange.ANY;
            case "paladin", "ranger" -> SpellChange.ONE;
            default -> SpellChange.NONE;
        };
    }

    // The 2024 PHB "Prepared Spells" column by class level — the same table as
    // PREPARED_SPELLS in the client's utils/spell-preparation.ts. Kept apart
    // from ClassProgression.preparedSpellsFor on purpose: that one drives the
    // level-up spell picks, this one the cap on the prepared list.
    private static final Map<String, int[]> PREPARED_CAP = Map.of(
            "bard",     new int[] {4, 5, 6, 7, 9, 10, 11, 12, 14, 15, 16, 16, 17, 17, 18, 18, 19, 20, 21, 22},
            "cleric",   new int[] {4, 5, 6, 7, 9, 10, 11, 12, 14, 15, 16, 16, 17, 17, 18, 18, 19, 20, 21, 22},
            "druid",    new int[] {4, 5, 6, 7, 9, 10, 11, 12, 14, 15, 16, 16, 17, 17, 18, 18, 19, 20, 21, 22},
            "paladin",  new int[] {2, 3, 4, 5, 6, 6, 7, 7, 9, 9, 10, 10, 11, 11, 12, 12, 14, 14, 15, 15},
            "ranger",   new int[] {2, 3, 4, 5, 6, 6, 7, 7, 9, 9, 10, 10, 11, 11, 12, 12, 14, 14, 15, 15},
            "sorcerer", new int[] {2, 4, 6, 7, 9, 10, 11, 12, 14, 15, 16, 16, 17, 17, 18, 18, 19, 20, 21, 22},
            "warlock",  new int[] {2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14, 15, 15},
            "wizard",   new int[] {4, 5, 6, 7, 9, 10, 11, 12, 14, 15, 16, 16, 17, 18, 19, 21, 22, 23, 24, 25});

    /** The prepared-spell cap, or null when the class has no table (no cap is enforced). */
    static Integer preparedSpellCap(String clazz, Short level) {
        int[] row = PREPARED_CAP.get(key(clazz));
        if (row == null) {
            return null;
        }
        int lvl = level == null ? 1 : level;
        return row[Math.min(Math.max(lvl, 1), row.length) - 1];
    }

    // ── Prepared spells ────────────────────────────────────────────────────────

    /** Lower-cased names of the hero's leveled spells that count as prepared right now. */
    static Set<String> preparedNow(PC pc) {
        Set<String> names = new LinkedHashSet<>();
        for (Map<String, Object> spell : JSON.parse(pc.getSpells())) {
            if (isLeveled(spell) && !Boolean.FALSE.equals(spell.get("prepared"))) {
                names.add(key(spellName(spell)));
            }
        }
        return names;
    }

    /**
     * Check a requested prepared list against the hero's sheet; 400 with a
     * message the player can act on. Rules:
     * <ul>
     *   <li>every name is a leveled spell already on the hero's spell list
     *       (cantrips are always prepared and are not part of the choice);</li>
     *   <li>a class that cannot change spells at a long rest sends its current
     *       list or nothing;</li>
     *   <li>a Paladin or Ranger differs from the current list by at most one
     *       spell out and one spell in (so a swap, or adding one under the cap,
     *       or dropping one);</li>
     *   <li>adding a spell never takes the count over the cap. Only-removals
     *       are always allowed, exactly as on the sheet, so a hero who is over
     *       the cap from older data can still come down.</li>
     * </ul>
     */
    static void validatePrepared(PC pc, List<String> requested) {
        if (requested == null) {
            return;
        }
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Map<String, Object> spell : JSON.parse(pc.getSpells())) {
            byName.putIfAbsent(key(spellName(spell)), spell);
        }
        Set<String> wanted = new LinkedHashSet<>();
        for (String name : requested) {
            if (name == null || name.isBlank()) {
                throw bad("A prepared spell is missing its name");
            }
            Map<String, Object> spell = byName.get(key(name));
            if (spell == null) {
                throw bad(name.trim() + " is not on " + pc.getName() + "'s spell list");
            }
            if (!isLeveled(spell)) {
                throw bad(name.trim() + " is a cantrip. Cantrips are always prepared and are not part of this choice");
            }
            wanted.add(key(name));
        }

        Set<String> current = preparedNow(pc);
        Set<String> added = new LinkedHashSet<>(wanted);
        added.removeAll(current);
        Set<String> removed = new LinkedHashSet<>(current);
        removed.removeAll(wanted);
        if (added.isEmpty() && removed.isEmpty()) {
            return;
        }

        SpellChange change = spellChangeFor(pc.getClazz());
        if (change == SpellChange.NONE) {
            throw bad(displayClass(pc) + " heroes do not change prepared spells at a long rest");
        }
        if (change == SpellChange.ONE && (added.size() > 1 || removed.size() > 1)) {
            throw bad(displayClass(pc) + " heroes can replace only one prepared spell at a long rest");
        }
        Integer cap = preparedSpellCap(pc.getClazz(), pc.getLevel());
        if (!added.isEmpty() && cap != null && wanted.size() > cap) {
            throw bad("That is " + wanted.size() + " prepared spells, and the limit is " + cap);
        }
    }

    /**
     * Write a validated prepared list onto the hero: every leveled spell gets
     * an explicit flag. Returns a short description of what changed, or null
     * when nothing did.
     */
    static String applyPrepared(PC pc, List<String> requested) {
        if (requested == null) {
            return null;
        }
        Set<String> wanted = new LinkedHashSet<>();
        requested.forEach(name -> wanted.add(key(name)));
        Set<String> current = preparedNow(pc);
        if (wanted.equals(current)) {
            return null;
        }
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<Map<String, Object>> spells = JSON.parse(pc.getSpells());
        for (Map<String, Object> spell : spells) {
            if (!isLeveled(spell)) {
                continue;
            }
            String name = key(spellName(spell));
            boolean now = wanted.contains(name);
            boolean was = current.contains(name);
            if (now && !was) {
                added.add(spellName(spell));
            } else if (!now && was) {
                removed.add(spellName(spell));
            }
            spell.put("prepared", now);
        }
        pc.setSpells(JSON.write(spells));

        if (added.size() + removed.size() > 4) {
            return "prepared spells changed (" + added.size() + " in, " + removed.size() + " out)";
        }
        List<String> parts = new ArrayList<>();
        if (!added.isEmpty()) {
            parts.add("prepared " + String.join(", ", added));
        }
        if (!removed.isEmpty()) {
            parts.add("unprepared " + String.join(", ", removed));
        }
        return String.join("; ", parts);
    }

    /**
     * Prepared spells change during a long rest, so an owner's whole-character
     * save must not move them: every leveled spell that was already on the
     * stored sheet keeps its stored {@code prepared} flag (or keeps having
     * none), whatever the body says. A spell that is new in the body — learned
     * or granted since — keeps the flag it arrives with, and a spell the body
     * drops is simply gone. Nothing is rejected, like the other server-owned
     * fields. Mutates {@code incoming}.
     */
    static void keepStoredPreparedFlags(PC incoming, PC existing) {
        Map<String, Map<String, Object>> stored = new LinkedHashMap<>();
        for (Map<String, Object> spell : JSON.parse(existing.getSpells())) {
            if (isLeveled(spell)) {
                stored.putIfAbsent(key(spellName(spell)), spell);
            }
        }
        if (stored.isEmpty() || incoming.getSpells() == null) {
            return;
        }
        List<Map<String, Object>> spells = JSON.parse(incoming.getSpells());
        boolean changed = false;
        for (Map<String, Object> spell : spells) {
            Map<String, Object> was = isLeveled(spell) ? stored.get(key(spellName(spell))) : null;
            if (was == null) {
                continue;
            }
            if (was.containsKey("prepared")) {
                if (!java.util.Objects.equals(was.get("prepared"), spell.get("prepared"))
                        || !spell.containsKey("prepared")) {
                    spell.put("prepared", was.get("prepared"));
                    changed = true;
                }
            } else if (spell.containsKey("prepared")) {
                spell.remove("prepared");
                changed = true;
            }
        }
        if (changed) {
            incoming.setSpells(JSON.write(spells));
        }
    }

    // ── Death Knight: Runeforging ──────────────────────────────────────────────

    /** A Death Knight who has Runeforging: Paladin + Oath of the Ebon Blade, level 3 or higher. */
    static boolean isRuneforger(PC pc) {
        return "paladin".equals(key(pc.getClazz()))
                && pc.getSubclass() != null
                && ClassProgression.EBON_BLADE.equalsIgnoreCase(pc.getSubclass().trim())
                && pc.getLevel() != null && pc.getLevel() >= 3;
    }

    // Fallback for a weapon the catalog does not know (a custom or renamed
    // weapon, a free-text entry like "Handaxes (4)"): read what the entry does
    // carry — the Ammunition property, or a telling name. The client applies
    // the same two steps (utils/rune-weapon.ts).
    private static final Pattern RANGED_NAME = Pattern.compile(
            "(?i)(bow\\b|\\bsling\\b|\\bdarts?\\b|\\bblowgun\\b|\\bmusket\\b|\\bpistol\\b|\\bnet\\b)");

    static boolean isRanged(String name, Object properties) {
        return isRanged(name, null, properties);
    }

    /**
     * Whether a weapon on a sheet is ranged. A catalog weapon (matched by its
     * catalog key, else by its exact name) answers from the catalog's own
     * melee/ranged data ({@link WeaponCatalog}); anything else falls back to
     * the Ammunition property and the name.
     */
    static boolean isRanged(String name, String catalogKey, Object properties) {
        Optional<Boolean> known = WeaponCatalog.ranged(catalogKey, name);
        if (known.isPresent()) {
            return known.get();
        }
        if (properties instanceof List<?> list) {
            for (Object p : list) {
                if (p != null && p.toString().trim().toLowerCase(Locale.ROOT).startsWith("ammunition")) {
                    return true;
                }
            }
        }
        return name != null && RANGED_NAME.matcher(name).find();
    }

    /**
     * The melee weapons the hero has, by name, in sheet order and without
     * repeats: inventory items of category weapon that are not dropped, then
     * the older free-text weapon list.
     */
    static List<String> meleeWeapons(PC pc) {
        Map<String, String> byKey = new LinkedHashMap<>();
        for (Map<String, Object> item : JSON.parse(pc.getInventory())) {
            String name = text(item.get("name"));
            if (name == null || !"weapon".equalsIgnoreCase(text(item.get("category")))
                    || "dropped".equalsIgnoreCase(text(item.get("status")))
                    || isRanged(name, text(item.get("catalogKey")), item.get("properties"))) {
                continue;
            }
            byKey.putIfAbsent(key(name), name);
        }
        for (Map<String, Object> weapon : JSON.parse(pc.getWeapons())) {
            String name = text(weapon.get("name"));
            if (name != null && !isRanged(name, null)) {
                byKey.putIfAbsent(key(name), name);
            }
        }
        return new ArrayList<>(byKey.values());
    }

    /** The hero's melee weapon with this name (in the sheet's own spelling), or null. */
    private static String matchMeleeWeapon(PC pc, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return meleeWeapons(pc).stream().filter(w -> key(w).equals(key(name))).findFirst().orElse(null);
    }

    /**
     * The weapon the rune is on right now, or null: no rune weapon recorded,
     * the hero is not a Runeforging Death Knight, or the recorded weapon is no
     * longer on the sheet (sold, dropped, renamed) — which counts as unset.
     */
    static String runeWeaponOf(PC pc) {
        return isRuneforger(pc) ? matchMeleeWeapon(pc, pc.getRuneWeapon()) : null;
    }

    /** Check a requested rune weapon; 400 with a message the player can act on. */
    static void validateRuneWeapon(PC pc, String requested) {
        if (requested == null || requested.isBlank()) {
            return;
        }
        if (!isRuneforger(pc)) {
            throw bad("Only a Death Knight of level 3 or higher has a rune weapon");
        }
        if (matchMeleeWeapon(pc, requested) == null) {
            throw bad(requested.trim() + " is not a melee weapon " + pc.getName() + " has");
        }
    }

    /**
     * A Death Knight who has a melee weapon but no rune on one must choose the
     * rune's weapon as they ready up; 400 otherwise. Checked at Ready only —
     * the client blocks the button the same way.
     */
    static void requireRuneChoice(PC pc, LongRestChoices choices) {
        if (!isRuneforger(pc) || meleeWeapons(pc).isEmpty() || runeWeaponOf(pc) != null) {
            return;
        }
        if (choices == null || choices.runeWeapon() == null || choices.runeWeapon().isBlank()) {
            throw bad("Choose the weapon that carries " + pc.getName() + "'s rune before readying");
        }
    }

    /** Move the rune to a validated weapon. Returns what changed, or null when nothing did. */
    static String applyRuneWeapon(PC pc, String requested) {
        String target = matchMeleeWeapon(pc, requested);
        if (target == null || !isRuneforger(pc)) {
            return null;
        }
        String before = runeWeaponOf(pc);
        pc.setRuneWeapon(target);
        if (target.equals(before)) {
            return null;
        }
        String rune = runeName(pc);
        return before == null
                ? rune + " inscribed on " + target
                : rune + " moved from " + before + " to " + target;
    }

    /** "Blood Rune" / "Rune" when the Death Knight has no rune type recorded. */
    static String runeName(PC pc) {
        String spec = pc.getSpecialization();
        return spec == null || spec.isBlank() ? "Rune" : spec.trim() + " Rune";
    }

    /**
     * Temporary hit points a Blood-rune Death Knight gains on finishing a long
     * rest with the rune inscribed: Charisma modifier + Paladin level, never
     * below zero. Zero for everyone else.
     */
    static int bloodRuneTempHp(PC pc) {
        if (runeWeaponOf(pc) == null || !"blood".equals(key(pc.getSpecialization()))) {
            return 0;
        }
        int cha = pc.getAbilityCha() == null ? 10 : pc.getAbilityCha();
        return Math.max(0, ClassProgression.abilityModifier(cha) + pc.getLevel());
    }

    /**
     * The rune weapon to store when a whole-character save arrives (create,
     * owner update, DM update). The body's own value wins when it names a
     * melee weapon on the body's sheet; a body without the field keeps the
     * stored one (so a payload built without it cannot erase the rune); and
     * anything that no longer fits — not a Runeforging Death Knight any more,
     * or the weapon is gone — is stored as unset rather than rejected, because
     * a sheet save must not fail over a weapon the player just sold.
     */
    static String normalizeRuneWeapon(PC incoming, PC existing) {
        if (!isRuneforger(incoming)) {
            return null;
        }
        String candidate = incoming.getRuneWeapon() != null
                ? incoming.getRuneWeapon()
                : existing == null ? null : existing.getRuneWeapon();
        return matchMeleeWeapon(incoming, candidate);
    }

    // ── Both choices together ──────────────────────────────────────────────────

    /** Validate everything a Ready carries; 400 on the first problem. */
    static void validate(PC pc, LongRestChoices choices) {
        if (choices == null) {
            return;
        }
        validatePrepared(pc, choices.preparedSpells());
        validateRuneWeapon(pc, choices.runeWeapon());
    }

    /** The seat's pending choices as stored JSON, or null when there are none. */
    static String serialize(LongRestChoices choices) {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        if (choices.preparedSpells() != null) {
            map.put("preparedSpells", choices.preparedSpells());
        }
        if (choices.runeWeapon() != null && !choices.runeWeapon().isBlank()) {
            map.put("runeWeapon", choices.runeWeapon().trim());
        }
        return JSON.writeObject(map);
    }

    /** Read a seat's stored choices back; null when there are none. */
    static LongRestChoices parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        Map<String, Object> map = JSON.parseObject(stored);
        List<String> prepared = null;
        if (map.get("preparedSpells") instanceof List<?> list) {
            prepared = list.stream().map(String::valueOf).toList();
        }
        LongRestChoices choices = new LongRestChoices(prepared, text(map.get("runeWeapon")));
        return choices.isEmpty() ? null : choices;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static boolean isLeveled(Map<String, Object> spell) {
        return spell.get("lvl") instanceof Number n && n.intValue() > 0;
    }

    private static String spellName(Map<String, Object> spell) {
        String name = text(spell.get("name"));
        return name == null ? "" : name;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String displayClass(PC pc) {
        String clazz = pc.getClazz() == null ? "" : pc.getClazz().trim();
        return clazz.isEmpty() ? "These" : Character.toUpperCase(clazz.charAt(0)) + clazz.substring(1);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
