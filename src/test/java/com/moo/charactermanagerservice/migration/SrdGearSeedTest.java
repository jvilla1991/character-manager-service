package com.moo.charactermanagerservice.migration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Guards the V25 adventuring-gear seed without a database: the survival-
 * conditions consume flow matches inventory lines by these exact catalog keys,
 * so a renamed or missing key would silently break ration/waterskin consumption.
 */
class SrdGearSeedTest {

    private String seed() throws Exception {
        return migration("V25__seed_srd_gear.sql");
    }

    private String containers() throws Exception {
        return migration("V30__supply_containers.sql");
    }

    private String migration(String file) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/db/migration/" + file)) {
            assertThat(in).as(file + " on classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void seedsTheTwoConsumables_underTheGearCategory() throws Exception {
        String sql = seed();
        assertThat(sql).contains("('rations',");
        assertThat(sql).contains("('waterskin',");
        // both rows sit in the GEAR category the shop now supports
        assertThat(sql.split("'GEAR'")).hasSize(3); // exactly two occurrences
    }

    @Test
    void pricesWeightsAndBulk_matchTheBooks() throws Exception {
        String sql = seed();
        assertThat(sql).contains("('rations',   'Rations (1 day)', 'GEAR', 50, 2, 1.0,");
        assertThat(sql).contains("('waterskin', 'Waterskin',       'GEAR', 20, 5, 1.0,");
    }

    @Test
    void isIdempotentOnItemKey() throws Exception {
        assertThat(seed()).contains("ON CONFLICT (item_key) DO NOTHING");
    }

    // --- V30: the container model (ration boxes / waterskins hold 5 servings) ---

    @Test
    void v30_repricesTheServings_andWeighsTheContainers() throws Exception {
        String sql = containers();
        // rations become tiny 1 sp servings; the waterskin weighs as a 1-bulk container
        assertThat(sql).contains(
                "UPDATE srd_item SET cost_cp = 10, weight = 0.2, bulk = 0.2 WHERE item_key = 'rations'");
        assertThat(sql).contains(
                "UPDATE srd_item SET cost_cp = 20, weight = 1, bulk = 1.0 WHERE item_key = 'waterskin'");
    }

    @Test
    void v30_addsTheRationBoxContainer_underGear_idempotently() throws Exception {
        String sql = containers();
        assertThat(sql).contains("('ration-box', 'Ration box', 'GEAR', 50, 1, 1.0, 'DD', '{}')");
        assertThat(sql).contains("ON CONFLICT (item_key) DO NOTHING");
    }
}
