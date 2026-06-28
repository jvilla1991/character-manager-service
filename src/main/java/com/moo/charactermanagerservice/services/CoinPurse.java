package com.moo.charactermanagerservice.services;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Coin-denomination math for purchases. Wealth is summed in copper and a
 * purchase is "spend with change-making": deduct the price from the total, then
 * re-mint the remainder into denominations. Change is returned in pp/gp/sp/cp —
 * electrum is never minted (it's the oddball coin), so any electrum the buyer
 * held is folded into its gp-equivalent as part of the settlement.
 *
 * <p>Copper values (1 gp = 100 cp): cp=1, sp=10, ep=50, gp=100, pp=1000 — the
 * same rates the frontend coin-purse uses.
 */
final class CoinPurse {

    private CoinPurse() {}

    private static final int CP = 1, SP = 10, EP = 50, GP = 100, PP = 1000;

    /** Total wealth of a {cp,sp,ep,gp,pp} purse, in copper. Null/missing keys = 0. */
    static long toCopper(Map<String, ?> coins) {
        if (coins == null) return 0L;
        return n(coins, "cp") * CP
                + n(coins, "sp") * SP
                + n(coins, "ep") * EP
                + n(coins, "gp") * GP
                + n(coins, "pp") * PP;
    }

    /** Distribute a copper amount greedily into pp/gp/sp/cp (ep always 0). */
    static Map<String, Integer> fromCopper(long copper) {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("cp", 0);
        out.put("sp", 0);
        out.put("ep", 0); // never minted as change
        out.put("gp", 0);
        out.put("pp", 0);
        out.put("pp", (int) (copper / PP)); copper %= PP;
        out.put("gp", (int) (copper / GP)); copper %= GP;
        out.put("sp", (int) (copper / SP)); copper %= SP;
        out.put("cp", (int) copper);
        return out;
    }

    /**
     * Deduct {@code priceCp} from the purse and return the new purse. Throws 409
     * if total wealth can't cover the price.
     */
    static Map<String, Integer> deduct(Map<String, ?> coins, long priceCp) {
        long total = toCopper(coins);
        if (total < priceCp) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient funds");
        }
        return fromCopper(total - priceCp);
    }

    private static long n(Map<String, ?> m, String key) {
        Object v = m.get(key);
        return v instanceof Number num ? num.longValue() : 0L;
    }
}
