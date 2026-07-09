package com.moo.charactermanagerservice.services;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * The container model: ration boxes / waterskins are containers (5 servings
 * each); rations and water are the charge lines the clock consumes. Mirrors
 * the frontend's utils/survival.ts — demo mode must behave identically.
 */
class SurvivalSuppliesTest {

    private static Map<String, Object> line(String key, int qty) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("catalogKey", key);
        l.put("qty", qty);
        return l;
    }

    private static Map<String, Object> find(List<Map<String, Object>> inv, String key) {
        return inv.stream().filter(l -> key.equals(l.get("catalogKey"))).findFirst().orElse(null);
    }

    // --- seed ---

    @Test
    void seed_grantsTheStartingKit_oneBoxOneSkinFiveRationsFiveWater() {
        List<Map<String, Object>> inv = new ArrayList<>();

        SurvivalSupplies.seed(inv);

        assertThat(find(inv, "ration-box")).containsEntry("qty", 1).containsEntry("bulk", 1.0);
        assertThat(find(inv, "waterskin")).containsEntry("qty", 1).containsEntry("bulk", 1.0);
        assertThat(find(inv, "rations")).containsEntry("qty", 5);
        assertThat(find(inv, "water")).containsEntry("qty", 5);
        // water is never purchasable — no price stamped
        assertThat(find(inv, "water")).doesNotContainKey("unitCostCp");
    }

    @Test
    void seed_isIdempotent_neverRefillsALiveLine() {
        List<Map<String, Object>> inv = new ArrayList<>(List.of(
                line("ration-box", 2), line("waterskin", 1), line("rations", 0), line("water", 3)));

        SurvivalSupplies.seed(inv);

        assertThat(inv).hasSize(4);
        assertThat(find(inv, "rations")).containsEntry("qty", 0); // stays empty
        assertThat(find(inv, "ration-box")).containsEntry("qty", 2);
    }

    // --- normalize (legacy migration) ---

    @Test
    void normalize_addsTheImpliedFreeBox_whenMissing() {
        List<Map<String, Object>> inv = new ArrayList<>(List.of(line("rations", 3)));

        SurvivalSupplies.normalize(inv);

        assertThat(find(inv, "ration-box")).containsEntry("qty", 1);
    }

    @Test
    void normalize_movesLegacyWaterskinChargesToAWaterLine_andKeepsEnoughSkins() {
        // Old model: waterskin qty WAS the water servings (here 7).
        List<Map<String, Object>> inv = new ArrayList<>(List.of(line("waterskin", 7)));

        SurvivalSupplies.normalize(inv);

        assertThat(find(inv, "water")).containsEntry("qty", 7);
        assertThat(find(inv, "waterskin")).containsEntry("qty", 2); // ceil(7 / 5)
    }

    @Test
    void normalize_keepsAtLeastOneSkin_evenForAnEmptyLegacySkin() {
        List<Map<String, Object>> inv = new ArrayList<>(List.of(line("waterskin", 0)));

        SurvivalSupplies.normalize(inv);

        assertThat(find(inv, "water")).containsEntry("qty", 0);
        assertThat(find(inv, "waterskin")).containsEntry("qty", 1); // max(1, ceil(0/5))
    }

    @Test
    void normalize_isIdempotent_onAlreadyConvertedData() {
        List<Map<String, Object>> inv = new ArrayList<>(List.of(
                line("ration-box", 2), line("waterskin", 1), line("rations", 4), line("water", 4)));

        SurvivalSupplies.normalize(inv);

        assertThat(inv).hasSize(4);
        assertThat(find(inv, "waterskin")).containsEntry("qty", 1);
        assertThat(find(inv, "water")).containsEntry("qty", 4);
    }

    // --- tryConsume ---

    @Test
    void tryConsume_decrementsToZero_andKeepsTheLine() {
        List<Map<String, Object>> inv = new ArrayList<>(List.of(line("rations", 1)));

        assertThat(SurvivalSupplies.tryConsume(inv, "rations")).isTrue();

        assertThat(inv).hasSize(1); // no more line removal — the box persists empty
        assertThat(find(inv, "rations")).containsEntry("qty", 0);
    }

    @Test
    void tryConsume_returnsFalse_atZeroCharges() {
        List<Map<String, Object>> inv = new ArrayList<>(List.of(line("water", 0)));

        assertThat(SurvivalSupplies.tryConsume(inv, "water")).isFalse();
    }

    @Test
    void tryConsume_drinksFromTheWaterLine_neverTheSkin() {
        List<Map<String, Object>> inv = new ArrayList<>(List.of(
                line("waterskin", 1), line("water", 2)));

        assertThat(SurvivalSupplies.tryConsume(inv, "water")).isTrue();

        assertThat(find(inv, "water")).containsEntry("qty", 1);
        assertThat(find(inv, "waterskin")).containsEntry("qty", 1); // untouched container
    }

    @Test
    void tryConsume_skipsDroppedLines() {
        Map<String, Object> dropped = line("rations", 5);
        dropped.put("status", "dropped");
        List<Map<String, Object>> inv = new ArrayList<>(List.of(dropped));

        assertThat(SurvivalSupplies.tryConsume(inv, "rations")).isFalse();
    }

    // --- capacity ---

    @Test
    void capacity_isContainersTimesFive() {
        List<Map<String, Object>> inv = new ArrayList<>(List.of(
                line("ration-box", 2), line("waterskin", 1)));

        assertThat(SurvivalSupplies.capacityFor(inv, "rations")).isEqualTo(10);
        assertThat(SurvivalSupplies.capacityFor(inv, "water")).isEqualTo(5);
    }

    @Test
    void capacity_isZero_withNoContainers() {
        assertThat(SurvivalSupplies.capacityFor(new ArrayList<>(), "rations")).isZero();
    }
}
