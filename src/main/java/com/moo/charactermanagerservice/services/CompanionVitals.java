package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.FollowerCombatantView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The part of a server-owned companion's stat block that play changes: its current Hit Points,
 * its temporary Hit Points and its conditions. Everything else on a {@code COMPANION} follower is
 * worked out by the server from the character it belongs to, so these three values are all that
 * is ever carried from one block to another. Two moments do that:
 * <ul>
 *   <li>a follower update ({@link #edited}): the stored block stays, and only these three values
 *       are read from the request's block;</li>
 *   <li>a recompute ({@link #carriedOver}): a freshly built block replaces the stored one and
 *       takes these three values from it.</li>
 * </ul>
 * Either way the result is clean: current Hit Points between 0 and the maximum, temporary Hit
 * Points never negative, and no condition the block says the companion is immune to.
 *
 * <p>Pure functions over the sheet-shaped stat block (see {@code FollowerCombatantView} for the
 * fields: {@code {"hp":{"cur":9,"max":16,"temp":0},"conditions":[...]}}). Reading is forgiving:
 * a missing or wrongly typed value is treated as not there.
 */
final class CompanionVitals {

    private CompanionVitals() {}

    /** The most conditions a companion carries, and the longest a condition's name may be. */
    static final int MAX_CONDITIONS = 30;
    static final int MAX_CONDITION_LENGTH = 40;

    private static Map<?, ?> hpOf(Map<String, Object> block) {
        return block != null && block.get("hp") instanceof Map<?, ?> hp ? hp : Map.of();
    }

    private static Integer whole(Object value) {
        return FollowerCombatantView.whole(value);
    }

    /**
     * A stored block after an update from the owning player or the DM: the block as stored, with
     * the request's current Hit Points, temporary Hit Points and conditions. A value the request
     * leaves out, or sends in the wrong shape, keeps the stored one. Nothing else in the request
     * is read, so a request that changes Armor Class or the attack changes nothing.
     */
    static Map<String, Object> edited(Map<String, Object> stored, Map<String, Object> request) {
        Map<?, ?> storedHp = hpOf(stored);
        Map<?, ?> sentHp = hpOf(request);
        Integer current = whole(sentHp.get("cur")) != null ? whole(sentHp.get("cur")) : whole(storedHp.get("cur"));
        Integer temporary = whole(sentHp.get("temp")) != null ? whole(sentHp.get("temp")) : whole(storedHp.get("temp"));
        Object conditions = request != null && request.get("conditions") instanceof List<?>
                ? request.get("conditions") : stored.get("conditions");
        return with(new LinkedHashMap<>(stored), current, temporary, conditions);
    }

    /**
     * A freshly built block that takes over from the stored one: it keeps the stored temporary
     * Hit Points and conditions, and the stored current Hit Points raised by however much the
     * maximum grew (a higher level gives the companion those Hit Points at once; a lower maximum
     * takes none away beyond the new maximum). A stored block without readable Hit Points leaves
     * the fresh block at full.
     */
    static Map<String, Object> carriedOver(Map<String, Object> fresh, Map<String, Object> stored) {
        Map<?, ?> storedHp = hpOf(stored);
        Integer freshMax = whole(hpOf(fresh).get("max"));
        Integer storedMax = whole(storedHp.get("max"));
        Integer current = whole(storedHp.get("cur"));
        if (current != null && freshMax != null && storedMax != null) {
            current += Math.max(0, freshMax - storedMax);
        }
        if (current == null) current = freshMax;
        return with(new LinkedHashMap<>(fresh), current, whole(storedHp.get("temp")),
                stored == null ? null : stored.get("conditions"));
    }

    /** Put the three values on a block (a copy the caller owns), cleaned against that block. */
    private static Map<String, Object> with(Map<String, Object> block, Integer current, Integer temporary,
                                            Object conditions) {
        Map<String, Object> hp = new LinkedHashMap<>();
        hpOf(block).forEach((key, value) -> hp.put(String.valueOf(key), value));
        Integer max = whole(hp.get("max"));
        int cur = Math.max(0, current == null ? 0 : current);
        hp.put("cur", max == null ? cur : Math.min(cur, Math.max(0, max)));
        hp.put("temp", Math.max(0, temporary == null ? 0 : temporary));
        block.put("hp", hp);
        block.put("conditions", conditions(conditions, block.get("conditionImmunities")));
        return block;
    }

    /**
     * The conditions to store: names only, each once (case ignored), none the companion is
     * immune to, bounded in number and length.
     */
    private static List<String> conditions(Object sent, Object immunities) {
        List<String> immune = new ArrayList<>();
        if (immunities instanceof List<?> list) {
            list.forEach(name -> immune.add(String.valueOf(name).trim().toLowerCase(Locale.ROOT)));
        }
        List<String> kept = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        if (!(sent instanceof List<?> list)) return kept;
        for (Object item : list) {
            if (!(item instanceof String text) || text.isBlank()) continue;
            String name = text.trim();
            String key = name.toLowerCase(Locale.ROOT);
            if (name.length() > MAX_CONDITION_LENGTH || immune.contains(key) || seen.contains(key)) continue;
            if (kept.size() >= MAX_CONDITIONS) break;
            seen.add(key);
            kept.add(name);
        }
        return kept;
    }
}
