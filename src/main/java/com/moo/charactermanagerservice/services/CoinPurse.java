package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Coin-denomination math for purchases, sales and loot payouts. The game uses
 * three coins — copper, silver and gold (1 gp = 10 sp = 100 cp) — and a purse
 * is the JSON object {@code {cp, sp, gp}}. Wealth is summed in copper and
 * every settlement is "spend with change-making": add or deduct the amount,
 * then re-mint the whole purse into gp/sp/cp.
 *
 * <p>Electrum and platinum are not part of the game (removed from the sheet
 * on 2026-11-13, from minting by V49, from stored purses by V50).
 * {@link #normalizeJson} is the one place that still knows them: it converts
 * a purse sent by an older client so they can never be stored again.
 */
final class CoinPurse {

    private CoinPurse() {}

    private static final int CP = 1, SP = 10, GP = 100;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Total wealth of a {cp,sp,gp} purse, in copper. Null/missing keys = 0. */
    static long toCopper(Map<String, ?> coins) {
        if (coins == null) return 0L;
        return n(coins, "cp") * CP
                + n(coins, "sp") * SP
                + n(coins, "gp") * GP;
    }

    /** Distribute a copper amount greedily into {cp, sp, gp}. */
    static Map<String, Integer> fromCopper(long copper) {
        Map<String, Integer> out = new LinkedHashMap<>();
        int gp = (int) (copper / GP); copper %= GP;
        int sp = (int) (copper / SP); copper %= SP;
        out.put("cp", (int) copper);
        out.put("sp", sp);
        out.put("gp", gp);
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

    /** Add {@code gainCp} (a sale or loot payout) to the purse and return the new purse. */
    static Map<String, Integer> add(Map<String, ?> coins, long gainCp) {
        return fromCopper(toCopper(coins) + gainCp);
    }

    /**
     * Normalize a purse a client is saving onto a character. An older client
     * (a tab opened before the three-coin release) still sends {@code ep} and
     * {@code pp}: they are converted — 1 ep = 5 sp, 1 pp = 10 gp, value kept —
     * and the purse is rewritten as {@code {cp, sp, gp}}. Anything else (no
     * legacy keys, blank, or unreadable JSON) is returned unchanged, so a save
     * can never lose coins it doesn't understand.
     */
    static String normalizeJson(String coinsJson) {
        if (coinsJson == null || coinsJson.isBlank()) return coinsJson;
        Map<String, Object> coins;
        try {
            coins = MAPPER.readValue(coinsJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return coinsJson;
        }
        if (coins == null || !(coins.containsKey("ep") || coins.containsKey("pp"))) return coinsJson;

        Map<String, Long> out = new LinkedHashMap<>();
        out.put("cp", n(coins, "cp"));
        out.put("sp", n(coins, "sp") + 5 * n(coins, "ep"));
        out.put("gp", n(coins, "gp") + 10 * n(coins, "pp"));
        try {
            return MAPPER.writeValueAsString(out);
        } catch (JsonProcessingException e) {
            return coinsJson;
        }
    }

    private static long n(Map<String, ?> m, String key) {
        Object v = m.get(key);
        return v instanceof Number num ? num.longValue() : 0L;
    }

    /**
     * Render a copper amount as a human-readable denomination string for the
     * activity log, e.g. {@code 150 -> "1 gp 5 sp"}, {@code 0 -> "0 cp"}.
     * Greedy gp/sp/cp, mirroring {@link #fromCopper}; zero-value denominations
     * are omitted except when the whole amount is 0.
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
