package com.moo.charactermanagerservice.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * Guards the costed material-component seed (V14) without a database. Asserts the
 * curated set is present at the right gp values, that it stays within the Phase-1
 * scope (spells of level ≤ 3), and that every {@code details} blob is valid JSON
 * carrying the spell link and the consumed-on-cast flag.
 */
class SrdMaterialComponentSeedTest {

    // ('key', 'Name', 'MATERIAL_COMPONENT', cost_cp, weight|NULL, 'SRD-2024', '{json}')
    private static final Pattern ROW = Pattern.compile(
            "^\\('([^']+)',\\s*'([^']+)',\\s*'MATERIAL_COMPONENT',\\s*(\\d+),\\s*(NULL|[\\d.]+),\\s*'SRD-2024',\\s*'(\\{.*})'\\)",
            Pattern.MULTILINE);

    private final ObjectMapper mapper = new ObjectMapper();

    private String seed() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/db/migration/V14__seed_srd_material_components.sql")) {
            assertThat(in).as("V14 material-component seed on classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void seedTargetsCatalog_andIsIdempotent() throws Exception {
        String sql = seed();
        assertThat(sql).contains("INSERT INTO srd_item");
        assertThat(sql).contains("ON CONFLICT (item_key) DO NOTHING");
    }

    @Test
    void seedsCuratedComponents_atExpectedPrices() throws Exception {
        Map<String, Long> prices = new HashMap<>();
        Matcher m = ROW.matcher(seed());
        while (m.find()) {
            prices.put(m.group(1), Long.parseLong(m.group(3)));
        }

        assertThat(prices).hasSize(13);
        assertThat(prices).containsKeys(
                "mc-chromatic-orb", "mc-find-familiar", "mc-identify", "mc-augury",
                "mc-continual-flame", "mc-magic-mouth", "mc-arcane-lock", "mc-warding-bond",
                "mc-clairvoyance", "mc-glyph-of-warding", "mc-magic-circle", "mc-nondetection",
                "mc-revivify");

        // Spot-check gp value × 100.
        assertThat(prices).containsEntry("mc-identify", 10000L);   // 100 gp pearl
        assertThat(prices).containsEntry("mc-revivify", 30000L);   // 300 gp diamonds
        assertThat(prices).containsEntry("mc-magic-mouth", 1000L); // 10 gp jade dust
    }

    @Test
    void everyComponent_isValidJson_withinScope_andRecordsConsumedFlag() throws Exception {
        Matcher m = ROW.matcher(seed());
        int rows = 0;
        while (m.find()) {
            rows++;
            long costCp = Long.parseLong(m.group(3));
            JsonNode d = mapper.readTree(m.group(5)); // throws if malformed

            assertThat(d.get("spell").asText()).isNotBlank();
            int level = d.get("spellLevel").asInt();
            assertThat(level).as("Phase 1 is capped at 3rd level").isBetween(1, 3);
            assertThat(d.get("consumedOnCast").isBoolean()).isTrue();
            // gpValue (in gp) must match the copper price.
            assertThat(d.get("gpValue").asLong() * 100).isEqualTo(costCp);
        }
        assertThat(rows).isEqualTo(13);
    }
}
