package com.moo.charactermanagerservice.migration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * Guards the Darker Dungeons bulk seed (V22) without a database: the UPDATE's
 * VALUES list must cover every weapon seeded by V11 and every armor item seeded
 * by V12 — a missing or misspelled key would silently no-op and leave the item
 * falling back to weight-band bulk at runtime.
 */
class SrdBulkSeedTest {

    // ('item-key', 3.0) rows inside the V22 VALUES list
    private static final Pattern BULK_ROW =
            Pattern.compile("\\('([a-z-]+)',\\s*([\\d.]+)\\)");

    // leading item_key of a V11/V12 INSERT row
    private static final Pattern SEED_KEY =
            Pattern.compile("^\\('([a-z-]+)',", Pattern.MULTILINE);

    private String resource(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/db/migration/" + name)) {
            assertThat(in).as(name + " on classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, BigDecimal> bulkValues() throws Exception {
        Map<String, BigDecimal> bulk = new HashMap<>();
        Matcher m = BULK_ROW.matcher(resource("V22__srd_item_bulk.sql"));
        while (m.find()) {
            bulk.put(m.group(1), new BigDecimal(m.group(2)));
        }
        return bulk;
    }

    private Set<String> seededKeys(String migration) throws Exception {
        Set<String> keys = new HashSet<>();
        Matcher m = SEED_KEY.matcher(resource(migration));
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    @Test
    void addsColumnIdempotently() throws Exception {
        assertThat(resource("V22__srd_item_bulk.sql"))
                .contains("ADD COLUMN IF NOT EXISTS bulk NUMERIC(4,1)");
    }

    @Test
    void coversEveryWeaponFromV11() throws Exception {
        Set<String> weapons = seededKeys("V11__seed_srd_weapons.sql");
        assertThat(weapons).isNotEmpty();
        assertThat(bulkValues().keySet()).containsAll(weapons);
    }

    @Test
    void coversEveryArmorItemFromV12() throws Exception {
        Set<String> armor = seededKeys("V12__seed_srd_armor.sql");
        assertThat(armor).hasSize(13);
        assertThat(bulkValues().keySet()).containsAll(armor);
    }

    @Test
    void usesOnlyOfficialBulkRatings() throws Exception {
        // DD bulk scale: Tiny 0.2, Small 1, Medium 2, Large 3, X-Large 6, XX-Large 9.
        Set<BigDecimal> scale = Set.of(
                new BigDecimal("0.2"), new BigDecimal("1.0"), new BigDecimal("2.0"),
                new BigDecimal("3.0"), new BigDecimal("6.0"), new BigDecimal("9.0"));
        assertThat(bulkValues().values()).allMatch(scale::contains);
    }

    @Test
    void spotChecksOfficialValues() throws Exception {
        Map<String, BigDecimal> bulk = bulkValues();
        assertThat(bulk).containsEntry("dart", new BigDecimal("0.2"));       // Tiny
        assertThat(bulk).containsEntry("dagger", new BigDecimal("1.0"));     // Small
        assertThat(bulk).containsEntry("longsword", new BigDecimal("3.0"));  // Large
        assertThat(bulk).containsEntry("chain-mail", new BigDecimal("9.0")); // heavy armor XXL
        assertThat(bulk).containsEntry("breastplate", new BigDecimal("6.0"));// medium armor XL
        assertThat(bulk).containsEntry("shield", new BigDecimal("2.0"));     // Medium
    }

    @Test
    void materialComponentsGetTinyDefault_withHandSizedOverrides() throws Exception {
        String sql = resource("V22__srd_item_bulk.sql");
        assertThat(sql).contains("SET bulk = 0.2 WHERE category = 'MATERIAL_COMPONENT'");
        assertThat(sql).contains("SET bulk = 1.0 WHERE item_key IN ('mc-augury', 'mc-clairvoyance')");
    }
}
