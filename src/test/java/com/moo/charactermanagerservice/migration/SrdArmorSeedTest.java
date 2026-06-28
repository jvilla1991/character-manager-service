package com.moo.charactermanagerservice.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * Guards the SRD 2024 armor seed (V12) without a database: parses the migration
 * resource and asserts the catalog slice has the expected items at the right
 * prices, and that every {@code details} blob is valid JSON (a malformed blob
 * would silently parse to an empty map at runtime). Slice-local — independent of
 * the weapons and material-component seeds.
 */
class SrdArmorSeedTest {

    // ('key', 'Name', 'ARMOR', cost_cp, weight, 'SRD-2024', '{json}')
    private static final Pattern ROW = Pattern.compile(
            "^\\('([^']+)',\\s*'([^']+)',\\s*'ARMOR',\\s*(\\d+),\\s*([\\d.]+),\\s*'SRD-2024',\\s*'(\\{.*})'\\)",
            Pattern.MULTILINE);

    private final ObjectMapper mapper = new ObjectMapper();

    private String seed() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/db/migration/V12__seed_srd_armor.sql")) {
            assertThat(in).as("V12 armor seed on classpath").isNotNull();
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
    void seedsAllThirteenArmorItems_atExpectedPrices() throws Exception {
        Map<String, Long> prices = new HashMap<>();
        Matcher m = ROW.matcher(seed());
        while (m.find()) {
            prices.put(m.group(1), Long.parseLong(m.group(3)));
        }

        assertThat(prices).hasSize(13);
        assertThat(prices).containsKeys(
                "padded", "leather", "studded-leather", "hide", "chain-shirt", "scale-mail",
                "breastplate", "half-plate", "ring-mail", "chain-mail", "splint", "plate", "shield");

        // Spot-check prices in copper (1 gp = 100 cp).
        assertThat(prices).containsEntry("padded", 500L);       // 5 gp
        assertThat(prices).containsEntry("leather", 1000L);     // 10 gp
        assertThat(prices).containsEntry("breastplate", 40000L);// 400 gp
        assertThat(prices).containsEntry("half-plate", 75000L); // 750 gp
        assertThat(prices).containsEntry("plate", 150000L);     // 1500 gp
        assertThat(prices).containsEntry("shield", 1000L);      // 10 gp
    }

    @Test
    void everyDetailsBlobIsValidJson_withArmorAttributes() throws Exception {
        Set<String> categories = Set.of("light", "medium", "heavy", "shield");
        Matcher m = ROW.matcher(seed());
        int rows = 0;
        while (m.find()) {
            rows++;
            JsonNode details = mapper.readTree(m.group(5)); // throws if malformed
            assertThat(details.get("armorCategory").asText()).isIn(categories);
            assertThat(details.get("armorClass").asText()).isNotBlank();
        }
        assertThat(rows).isEqualTo(13);
    }
}
