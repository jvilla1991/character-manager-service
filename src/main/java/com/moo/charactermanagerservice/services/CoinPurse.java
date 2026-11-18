package com.moo.charactermanagerservice.services;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Coin-denomination math for purchases, sales and loot payouts. Wealth is
 * summed in copper and every settlement is "spend with change-making": add or
 * deduct the amount, then re-mint the whole purse. Change is minted in
 * gp/sp/cp ONLY — the character sheet's purse shows just those three coins
 * (electrum and platinum left the display on 2026-11-13), so minting platinum
 * made gold vanish from view. Any electrum or platinum the character held is
 * folded into gold/silver as part of the settlement (V49 folded what was
 * already stored).
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

    /** Distribute a copper amount greedily into gp/sp/cp (ep and pp always 0 — never minted). */
    static Map<String, Integer> fromCopper(long copper) {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("cp", 0);
        out.put("sp", 0);
        out.put("ep", 0); // never minted as change
        out.put("gp", 0);
        out.put("pp", 0); // never minted as change — the sheet doesn't show it
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

    /** Add {@code gainCp} (a sale payout) to the purse and return the new purse. */
    static Map<String, Integer> add(Map<String, ?> coins, long gainCp) {
        return fromCopper(toCopper(coins) + gainCp);
    }

    private static long n(Map<String, ?> m, String key) {
        Object v = m.get(key);
        return v instanceof Number num ? num.longValue() : 0L;
    }

    /**
     * Render a copper amount as a human-readable denomination string for the
     * activity log, e.g. {@code 150 -> "1 gp 5 sp"}, {@code 0 -> "0 cp"}.
     * Greedy gp/sp/cp, mirroring {@link #fromCopper} (no electrum or platinum);
     * zero-value denominations are omitted except when the whole amount is 0.
     */
    static String format(long cp) {
        if (cp <= 0) return "0 cp";
        long gp = cp / GP; cp %= GP;
        long sp = cp / SP; cp %= SP;
        long remaining = cp;

        StringBuilder sb = new StringBuilder();
        if (gp > 0) sb.append(gp).append(" gp ");
        if (sp > 0) sb.append(sp).append(" sp ");
        if (remaining > 0) sb.append(remaining).append(" cp ");
        return sb.toString().trim();
    }
}
