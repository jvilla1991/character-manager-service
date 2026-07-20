package com.moo.charactermanagerservice.migration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * Guards the V36 adventuring-gear seed without a database: the DM-grant picker
 * and GEAR shops surface these rows by item_key, so a renamed key or a
 * re-seeded survival consumable (rations/waterskin are owned by V25/V30, which
 * re-price them) would corrupt the catalog on a fresh database.
 */
class SrdAdventuringGearSeedTest {

    private String seed() throws Exception {
        try (InputStream in = getClass()
                .getResourceAsStream("/db/migration/V36__seed_srd_adventuring_gear.sql")) {
            assertThat(in).as("V36 on classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void seedsTheCoreAdventuringGear_underTheGearCategory() throws Exception {
        String sql = seed();
        for (String key : new String[] {
                "backpack", "caltrops", "crowbar", "tinderbox", "rope", "torch",
                "oil", "potion-of-healing", "hooded-lantern", "bullseye-lantern",
                "explorers-pack", "druidic-focus-mistletoe", "healers-kit",
                "woodcarvers-tools", "thieves-tools", "herbalism-kit"}) {
            assertThat(sql).as(key).contains("('" + key + "',");
        }
    }

    @Test
    void everyRow_isGearCategory() throws Exception {
        Matcher rows = Pattern.compile("^\\('[a-z0-9-]+',.*$", Pattern.MULTILINE).matcher(seed());
        int count = 0;
        while (rows.find()) {
            assertThat(rows.group()).contains("'GEAR'");
            count++;
        }
        assertThat(count).isGreaterThan(100); // full gear + tools + packs slice
    }

    @Test
    void doesNotReseedTheSurvivalConsumables() throws Exception {
        String sql = seed();
        // owned (and re-priced!) by V25/V30 — V36 must not insert them
        assertThat(sql).doesNotContain("('rations',");
        assertThat(sql).doesNotContain("('waterskin',");
        assertThat(sql).doesNotContain("('ration-box',");
    }

    @Test
    void spotChecksSrdPricesAndWeights() throws Exception {
        String sql = seed();
        // SRD 5.2: Backpack 2 gp / 5 lb; Rope 1 gp / 5 lb (2024 halved the weight);
        // Torch 1 cp / 1 lb; Potion of Healing 50 gp / 0.5 lb
        assertThat(sql).contains("('backpack',             'Backpack',                     'GEAR',    200, 5,");
        assertThat(sql).contains("('rope',                 'Rope (50 feet)',               'GEAR',    100, 5,");
        assertThat(sql).contains("('torch',                'Torch',                        'GEAR',      1, 1,");
        assertThat(sql).contains("('potion-of-healing',    'Potion of Healing',            'GEAR',   5000, 0.5,");
    }

    @Test
    void isIdempotentOnItemKey() throws Exception {
        assertThat(seed()).contains("ON CONFLICT (item_key) DO NOTHING");
    }
}
