package com.moo.charactermanagerservice.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Darker Dungeons survival supplies: the Rations box and Waterskin every
 * character in a survival-conditions campaign carries. Each starts with 5
 * charges/servings; the passage of time auto-consumes them to keep the party
 * fed (see {@code SessionService#advanceTime}) rather than the player eating by
 * hand. Values mirror the V25 catalog seed so a granted/seeded line looks
 * exactly like a purchased one.
 */
final class SurvivalSupplies {

    static final int STARTING_CHARGES = 5;

    private SurvivalSupplies() {}

    /**
     * Add a full Rations box and Waterskin to the inventory if a live line for
     * each isn't already present (idempotent — call is guarded by the survival
     * {@code seeded} flag so a consumed-to-zero stack is never refilled).
     */
    static void seed(List<Map<String, Object>> inventory) {
        ensure(inventory, "rations", line("rations", "Rations (1 day)", 2, 50));
        ensure(inventory, "waterskin", line("waterskin", "Waterskin", 5, 20));
    }

    private static void ensure(List<Map<String, Object>> inventory, String catalogKey,
                               Map<String, Object> newLine) {
        boolean present = inventory.stream().anyMatch(l ->
                catalogKey.equals(l.get("catalogKey")) && !"dropped".equals(l.get("status")));
        if (!present) inventory.add(newLine);
    }

    private static Map<String, Object> line(String catalogKey, String name, int weight, int costCp) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("catalogKey", catalogKey);
        item.put("name", name);
        item.put("category", "gear");
        item.put("qty", STARTING_CHARGES);
        item.put("weight", weight);
        item.put("bulk", 1.0);
        item.put("unitCostCp", costCp);
        return item;
    }

    /**
     * Decrement one unit of the first live line with this catalog key (removed
     * at zero). Returns true if a unit was consumed — the caller offsets the
     * matching survival stage only when this is false (supplies ran out).
     */
    static boolean tryConsume(List<Map<String, Object>> inventory, String catalogKey) {
        for (var it = inventory.iterator(); it.hasNext(); ) {
            Map<String, Object> line = it.next();
            if (!catalogKey.equals(line.get("catalogKey"))) continue;
            if ("dropped".equals(line.get("status"))) continue;
            int qty = line.get("qty") instanceof Number n ? n.intValue() : 0;
            if (qty <= 0) continue;
            if (qty == 1) it.remove();
            else line.put("qty", qty - 1);
            return true;
        }
        return false;
    }
}
