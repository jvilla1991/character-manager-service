package com.moo.charactermanagerservice.services;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps {@link WeaponCatalog} honest: its melee/ranged table must say exactly
 * what the SRD weapon seed (V11) says, weapon for weapon. The seed is the
 * source of truth; this fails when either side changes without the other.
 */
class WeaponCatalogSeedTest {

    // ('key', 'Name', 'WEAPON', cost_cp, weight, 'SRD-2024', '{... "range":"melee" ...}')
    private static final Pattern ROW = Pattern.compile(
            "^\\('([^']+)',\\s*'([^']+)',\\s*'WEAPON',.*\"range\":\"(melee|ranged)\"", Pattern.MULTILINE);

    private static Map<String, String[]> seed() throws Exception {
        try (InputStream in = WeaponCatalogSeedTest.class
                .getResourceAsStream("/db/migration/V11__seed_srd_weapons.sql")) {
            assertThat(in).as("V11 weapon seed on classpath").isNotNull();
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String[]> rows = new LinkedHashMap<>();
            Matcher m = ROW.matcher(sql);
            while (m.find()) {
                rows.put(m.group(1), new String[] {m.group(2), m.group(3)});
            }
            return rows;
        }
    }

    @Test
    void theTableCoversEveryWeaponInTheSeed_andNothingElse() throws Exception {
        Map<String, String[]> seed = seed();
        assertThat(seed).hasSize(37); // the SRD 2024 weapon table
        assertThat(WeaponCatalog.RANGED_BY_KEY.keySet()).containsExactlyElementsOf(seed.keySet());
    }

    @Test
    void everyWeaponIsMeleeOrRanged_exactlyAsTheSeedSays_byKeyAndByName() throws Exception {
        for (Map.Entry<String, String[]> row : seed().entrySet()) {
            boolean ranged = "ranged".equals(row.getValue()[1]);
            assertThat(WeaponCatalog.ranged(row.getKey(), null))
                    .as("by key: %s", row.getKey()).isEqualTo(Optional.of(ranged));
            assertThat(WeaponCatalog.ranged(null, row.getValue()[0].toUpperCase()))
                    .as("by name: %s", row.getValue()[0]).isEqualTo(Optional.of(ranged));
        }
    }

    @Test
    void aWeaponTheCatalogDoesNotKnow_hasNoAnswer() {
        assertThat(WeaponCatalog.ranged(null, "Greataxe 'Splitter'")).isEmpty();
        assertThat(WeaponCatalog.ranged("frostmourne", "Frostmourne")).isEmpty();
        assertThat(WeaponCatalog.ranged(null, null)).isEmpty();
    }

    @Test
    void theKeyWins_overARenamedItem() {
        // A catalog longbow the player renamed is still a longbow.
        assertThat(WeaponCatalog.ranged("longbow", "Heartseeker")).contains(true);
        assertThat(WeaponCatalog.ranged("spear", "Boar Sticker")).contains(false);
    }
}
