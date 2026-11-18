package com.moo.charactermanagerservice.services;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** The three-coin (cp/sp/gp) change-making math behind purchases, sales and loot. */
class CoinPurseTest {

    @Test
    void toCopper_sumsCopperSilverGold() {
        Map<String, Object> purse = Map.of("cp", 5, "sp", 4, "gp", 2);
        // 5 + 40 + 200
        assertThat(CoinPurse.toCopper(purse)).isEqualTo(245L);
    }

    @Test
    void toCopper_treatsNullAndMissingKeysAsZero() {
        assertThat(CoinPurse.toCopper(null)).isZero();
        assertThat(CoinPurse.toCopper(Map.of("gp", 1))).isEqualTo(100L);
    }

    @Test
    void fromCopper_mintsExactlyCopperSilverGold() {
        Map<String, Integer> coins = CoinPurse.fromCopper(1234L);
        assertThat(coins).containsOnlyKeys("cp", "sp", "gp")
                .containsEntry("gp", 12).containsEntry("sp", 3).containsEntry("cp", 4);
    }

    @Test
    void deduct_makesChangeInGoldSilverCopper() {
        Map<String, Object> purse = Map.of("gp", 20);
        Map<String, Integer> after = CoinPurse.deduct(purse, 1525L); // 15 gp 2 sp 5 cp
        assertThat(after).containsOnlyKeys("cp", "sp", "gp")
                .containsEntry("gp", 4).containsEntry("sp", 7).containsEntry("cp", 5);
    }

    @Test
    void add_paysOutInGold() {
        Map<String, Integer> after = CoinPurse.add(Map.of("gp", 5), 6000L); // 5 gp + 60 gp
        assertThat(after).containsOnlyKeys("cp", "sp", "gp").containsEntry("gp", 65);
    }

    @Test
    void deduct_throws409_whenInsufficient() {
        Map<String, Object> purse = Map.of("gp", 10); // 1000 cp
        assertThatThrownBy(() -> CoinPurse.deduct(purse, 1500L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void deduct_allowsExactSpend_toZero() {
        Map<String, Object> purse = Map.of("gp", 15);
        assertThat(CoinPurse.deduct(purse, 1500L)).containsEntry("gp", 0).containsEntry("cp", 0);
    }

    // --- normalizeJson (a save from an older client) ---

    @Test
    void normalizeJson_convertsLegacyCoins_keepingTheValue() {
        String out = CoinPurse.normalizeJson("{\"cp\":3,\"sp\":4,\"ep\":2,\"gp\":5,\"pp\":6}");
        assertThat(out).isEqualTo("{\"cp\":3,\"sp\":14,\"gp\":65}"); // 2 ep = 10 sp, 6 pp = 60 gp
    }

    @Test
    void normalizeJson_dropsZeroLegacyKeys() {
        assertThat(CoinPurse.normalizeJson("{\"cp\":0,\"sp\":0,\"ep\":0,\"gp\":7,\"pp\":0}"))
                .isEqualTo("{\"cp\":0,\"sp\":0,\"gp\":7}");
    }

    @Test
    void normalizeJson_leavesThreeCoinBlankAndUnreadablePursesAlone() {
        assertThat(CoinPurse.normalizeJson("{\"cp\":1,\"sp\":2,\"gp\":3}")).isEqualTo("{\"cp\":1,\"sp\":2,\"gp\":3}");
        assertThat(CoinPurse.normalizeJson(null)).isNull();
        assertThat(CoinPurse.normalizeJson("")).isEmpty();
        assertThat(CoinPurse.normalizeJson("not json")).isEqualTo("not json");
    }

    // --- format (activity log display) ---

    @Test
    void format_singleCopper() {
        assertThat(CoinPurse.format(1L)).isEqualTo("1 cp");
    }

    @Test
    void format_tenCopper_asOneSilver() {
        assertThat(CoinPurse.format(10L)).isEqualTo("1 sp");
    }

    @Test
    void format_mixedGoldAndSilver() {
        assertThat(CoinPurse.format(150L)).isEqualTo("1 gp 5 sp");
    }

    @Test
    void format_largeAmountsStayInGold() {
        assertThat(CoinPurse.format(1250L)).isEqualTo("12 gp 5 sp");
    }

    @Test
    void format_zero() {
        assertThat(CoinPurse.format(0L)).isEqualTo("0 cp");
    }
}
