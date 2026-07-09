package com.moo.charactermanagerservice.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Darker Dungeons survival supplies under the CONTAINER model. Ration boxes and
 * waterskins are containers (1 bulk each, never auto-consumed); each holds
 * {@link #SERVINGS_PER_CONTAINER} servings. The servings live as separate
 * charge lines — {@code rations} (purchasable, 1 sp each) and {@code water}
 * (never sold; refilled free) — whose qty the passage of time decrements (see
 * {@code SessionService#advanceTime}). Charges are weightless inside their
 * containers and are capped at containers × 5 everywhere. Values mirror the
 * V25/V30 catalog seeds and the frontend {@code utils/survival.ts} so demo mode
 * behaves identically.
 */
final class SurvivalSupplies {

    static final int STARTING_CHARGES = 5;
    /** One container (ration box / waterskin) holds this many servings. */
    static final int SERVINGS_PER_CONTAINER = 5;

    private SurvivalSupplies() {}

    /**
     * Starting kit: 1 ration box, 1 waterskin, 5 rations, 5 water. Idempotent
     * per line (call is guarded by the survival {@code seeded} flag so consumed
     * charges are never refilled). Run {@link #normalize} first so legacy
     * waterskin charges convert before the water line is ensured.
     */
    static void seed(List<Map<String, Object>> inventory) {
        ensure(inventory, "ration-box", containerLine("ration-box", "Ration box", 50, 1));
        ensure(inventory, "waterskin", containerLine("waterskin", "Waterskin", 20, 1));
        ensure(inventory, "rations", rationsLine(STARTING_CHARGES));
        ensure(inventory, "water", waterLine(STARTING_CHARGES));
    }

    /**
     * Lazily migrate legacy (pre-container) supply data in place:
     * <ul>
     *   <li>no live {@code ration-box} line → add one (qty 1) — the old model's
     *       implied free box;</li>
     *   <li>no live {@code water} line but a live {@code waterskin} line → the
     *       skin's qty WAS the water charges: move it to a new {@code water}
     *       line and turn the skin into max(1, ceil(oldQty / 5)) containers.</li>
     * </ul>
     * Mirrors {@code normalizeSupplies} in the frontend's utils/survival.ts.
     */
    static void normalize(List<Map<String, Object>> inventory) {
        if (findLive(inventory, "ration-box") == null) {
            inventory.add(containerLine("ration-box", "Ration box", 50, 1));
        }
        Map<String, Object> skin = findLive(inventory, "waterskin");
        if (skin != null && findLive(inventory, "water") == null) {
            int oldQty = qtyOf(skin);
            inventory.add(waterLine(oldQty));
            skin.put("qty", Math.max(1, (int) Math.ceil(oldQty / (double) SERVINGS_PER_CONTAINER)));
            skin.put("weight", 1);
            skin.put("bulk", 1.0);
        }
    }

    /** Total servings a charge line can hold: its containers × 5. */
    static int capacityFor(List<Map<String, Object>> inventory, String chargeKey) {
        String containerKey = "rations".equals(chargeKey) ? "ration-box" : "waterskin";
        return countOf(inventory, containerKey) * SERVINGS_PER_CONTAINER;
    }

    /** Summed qty of every live line with this catalog key. */
    static int countOf(List<Map<String, Object>> inventory, String catalogKey) {
        return inventory.stream()
                .filter(l -> catalogKey.equals(l.get("catalogKey")) && !"dropped".equals(l.get("status")))
                .mapToInt(SurvivalSupplies::qtyOf)
                .sum();
    }

    /**
     * Decrement one unit of the first live line with this catalog key. The line
     * is kept at qty 0 (an empty box/skin persists for refilling — no more line
     * removal). Returns true if a unit was consumed — the caller offsets the
     * matching survival stage only when this is false (supplies ran out).
     */
    static boolean tryConsume(List<Map<String, Object>> inventory, String catalogKey) {
        for (Map<String, Object> line : inventory) {
            if (!catalogKey.equals(line.get("catalogKey"))) continue;
            if ("dropped".equals(line.get("status"))) continue;
            int qty = qtyOf(line);
            if (qty <= 0) continue;
            line.put("qty", qty - 1);
            return true;
        }
        return false;
    }

    // --- internals -----------------------------------------------------------

    /** Add the line unless a live line with this catalog key already exists. */
    private static void ensure(List<Map<String, Object>> inventory, String catalogKey,
                               Map<String, Object> newLine) {
        if (findLive(inventory, catalogKey) == null) inventory.add(newLine);
    }

    private static Map<String, Object> findLive(List<Map<String, Object>> inventory, String catalogKey) {
        return inventory.stream()
                .filter(l -> catalogKey.equals(l.get("catalogKey")) && !"dropped".equals(l.get("status")))
                .findFirst().orElse(null);
    }

    private static int qtyOf(Map<String, Object> line) {
        return line.get("qty") instanceof Number n ? n.intValue() : 0;
    }

    /** A container line (ration box / waterskin): 1 bulk, never auto-consumed. */
    private static Map<String, Object> containerLine(String catalogKey, String name, int costCp, int qty) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("catalogKey", catalogKey);
        item.put("name", name);
        item.put("category", "gear");
        item.put("qty", qty);
        item.put("weight", 1);
        item.put("bulk", 1.0);
        item.put("unitCostCp", costCp);
        return item;
    }

    /** Ration charges: tiny (0.2 lb / 0.2 bulk), 1 sp each — mirrors V30. */
    private static Map<String, Object> rationsLine(int qty) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("catalogKey", "rations");
        item.put("name", "Rations (1 day)");
        item.put("category", "gear");
        item.put("qty", qty);
        item.put("weight", 0.2);
        item.put("bulk", 0.2);
        item.put("unitCostCp", 10);
        return item;
    }

    /** Water charges: weightless inside the skin; never purchasable. */
    private static Map<String, Object> waterLine(int qty) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("catalogKey", "water");
        item.put("name", "Water");
        item.put("category", "gear");
        item.put("qty", qty);
        item.put("weight", 0);
        item.put("bulk", 0);
        return item;
    }
}
