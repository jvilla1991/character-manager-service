package com.moo.charactermanagerservice.services;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** The coin-denomination change-making math behind purchases. */
class CoinPurseTest {

    @Test
    void toCopper_sumsAllDenominations() {
        Map<String, Object> purse = Map.of("cp", 5, "sp", 4, "ep", 1, "gp", 2, "pp", 3);
        // 5 + 40 + 50 + 200 + 3000
        assertThat(CoinPurse.toCopper(purse)).isEqualTo(3295L);
    }

    @Test
    void toCopper_treatsNullAndMissingKeysAsZero() {
        assertThat(CoinPurse.toCopper(null)).isZero();
        assertThat(CoinPurse.toCopper(Map.of("gp", 1))).isEqualTo(100L);
    }

    @Test
    void fromCopper_distributesGreedily_andNeverMintsElectrum() {
        Map<String, Integer> coins = CoinPurse.fromCopper(1234L); // 1pp 2gp 3sp 4cp
        assertThat(coins).containsEntry("pp", 1).containsEntry("gp", 2)
                .containsEntry("sp", 3).containsEntry("cp", 4).containsEntry("ep", 0);
    }

    @Test
    void deduct_makesChangeAcrossDenominations() {
        // Pay 15 gp from a single platinum-heavy purse and get change back.
        Map<String, Object> purse = Map.of("pp", 2); // 2000 cp
        Map<String, Integer> after = CoinPurse.deduct(purse, 1500L); // longsword
        // 500 cp remainder = 5 gp
        assertThat(after).containsEntry("pp", 0).containsEntry("gp", 5)
                .containsEntry("sp", 0).containsEntry("cp", 0).containsEntry("ep", 0);
    }

    @Test
    void deduct_foldsElectrumIntoChange() {
        Map<String, Object> purse = Map.of("ep", 3); // 150 cp
        Map<String, Integer> after = CoinPurse.deduct(purse, 50L); // 100 cp left = 1 gp
        assertThat(after).containsEntry("gp", 1).containsEntry("ep", 0);
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
}
