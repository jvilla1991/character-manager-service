package com.moo.charactermanagerservice.services;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Server-authoritative casting math over the PC's JSON columns: find the spell
 * in the {@code spells} array, check/spend a slot in the {@code spellSlots}
 * object, and resolve material components against {@code inventory} lines. Pure
 * map-in/map-out helpers in the {@link SurvivalRules} mold — no Spring, no I/O;
 * the service decides which failure becomes which HTTP status.
 *
 * <p>Material components are matched BY NAME STRING: an inventory line counts
 * for a spell when its {@code spell} field equals the spell's name exactly.
 * Older PCs without {@code components}/{@code material} on their spells carry
 * no component requirement at all.
 */
final class SpellcastingRules {

    /**
     * A costly component's catalog text reads like "worth 50+ GP" — any digits
     * followed by an optional plus and "GP" marks the spell as needing a real,
     * non-substitutable component.
     */
    private static final Pattern COSTLY_MATERIAL =
            Pattern.compile("\\d+\\+?\\s*GP", Pattern.CASE_INSENSITIVE);

    private SpellcastingRules() {}

    /** The spell with this exact name from the parsed spells column, or null. */
    static Map<String, Object> findSpell(List<Map<String, Object>> spells, String name) {
        for (Map<String, Object> spell : spells) {
            if (name.equals(spell.get("name"))) return spell;
        }
        return null;
    }

    /** The spell's level; 0 (cantrip) when {@code lvl} is missing or non-numeric. */
    static int levelOf(Map<String, Object> spell) {
        return spell.get("lvl") instanceof Number n ? n.intValue() : 0;
    }

    /**
     * True when the parsed {@code spellSlots} object has an entry for this slot
     * level with {@code used < max}. Keys arrive as strings from JSON.
     */
    static boolean hasSlotAvailable(Map<String, Object> slots, int atLevel) {
        Map<String, Object> entry = slotEntry(slots, atLevel);
        if (entry == null) return false;
        return intOf(entry, "used") < intOf(entry, "max");
    }

    /**
     * Spend one slot at this level: {@code used + 1}, in place on the parsed
     * map. Callers check {@link #hasSlotAvailable} first; spending without a
     * free slot is a no-op rather than an overdraft.
     */
    static Map<String, Object> spendSlot(Map<String, Object> slots, int atLevel) {
        Map<String, Object> entry = slotEntry(slots, atLevel);
        if (entry != null && intOf(entry, "used") < intOf(entry, "max")) {
            entry.put("used", intOf(entry, "used") + 1);
        }
        return slots;
    }

    /**
     * The first live material-component line for this spell: category
     * {@code material-component}, {@code spell} equal to the spell's name,
     * not dropped, and quantity above zero. Null when the PC carries none.
     */
    static Map<String, Object> findComponentLine(List<Map<String, Object>> inventory,
                                                 String spellName) {
        for (Map<String, Object> line : inventory) {
            if (!isLiveComponentLine(line, spellName)) continue;
            return line;
        }
        return null;
    }

    /**
     * Decrement one unit of the first live component line for this spell; the
     * line is removed at zero. No matching line is fine — nothing changes.
     * Same loop shape as the survival consume.
     */
    static void consumeComponentLine(List<Map<String, Object>> inventory, String spellName) {
        for (Iterator<Map<String, Object>> it = inventory.iterator(); it.hasNext(); ) {
            Map<String, Object> line = it.next();
            if (!isLiveComponentLine(line, spellName)) continue;
            int qty = qtyOf(line);
            if (qty == 1) {
                it.remove();
            } else {
                line.put("qty", qty - 1);
            }
            return;
        }
    }

    /**
     * True when the spell demands a costly material component: its
     * {@code components} array contains "m" AND its {@code material} text names
     * a GP value (see {@link #COSTLY_MATERIAL}). Absent/null fields — older PCs
     * serialized before components existed — mean no requirement.
     */
    static boolean needsCostlyComponent(Map<String, Object> spell) {
        if (!(spell.get("components") instanceof List<?> components)) return false;
        boolean hasMaterial = components.stream()
                .anyMatch(c -> "m".equalsIgnoreCase(String.valueOf(c)));
        if (!hasMaterial) return false;
        return spell.get("material") instanceof String material
                && COSTLY_MATERIAL.matcher(material).find();
    }

    /** A usable component line for this spell: right category, right spell, live, in stock. */
    private static boolean isLiveComponentLine(Map<String, Object> line, String spellName) {
        if (!"material-component".equals(line.get("category"))) return false;
        if (!spellName.equals(line.get("spell"))) return false;
        if ("dropped".equals(line.get("status"))) return false;
        return qtyOf(line) > 0;
    }

    private static int qtyOf(Map<String, Object> line) {
        return line.get("qty") instanceof Number n ? n.intValue() : 0;
    }

    /** The {@code {max, used}} entry for a slot level, or null. JSON keys are strings. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> slotEntry(Map<String, Object> slots, int atLevel) {
        Object entry = slots.get(String.valueOf(atLevel));
        return entry instanceof Map ? (Map<String, Object>) entry : null;
    }

    private static int intOf(Map<String, Object> entry, String key) {
        return entry.get(key) instanceof Number n ? n.intValue() : 0;
    }
}
