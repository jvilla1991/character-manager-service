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
        try (InputStream in = getClass().getResourceAsStream("/db/migration/V25__seed_srd_gear.sql")) {
            assertThat(in).as("V25__seed_srd_gear.sql on classpath").isNotNull();
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
}
