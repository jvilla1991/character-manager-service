package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.SurvivalAction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Darker Dungeons ch. 31 survival-condition math: hunger / thirst / fatigue,
 * each an integer stage 0 (stuffed/quenched/energized) to 6 (starving/
 * dehydrated/barely awake). Pure map-in/map-out helpers over the JSON shape
 * stored in {@code pc.survival}; a NULL/empty stored value means all zeros.
 *
 * <p>Time of day worsens conditions (dawn +1 hunger +1 thirst, noon +1 fatigue,
 * dusk +1 all three; night is the sleep window and applies nothing). Eating,
 * drinking, and sleeping improve them. All results clamp to 0..6.
 */
final class SurvivalRules {

    static final int MIN_STAGE = 0;
    static final int MAX_STAGE = 6;

    private SurvivalRules() {}

    static int clamp(int stage) {
        return Math.max(MIN_STAGE, Math.min(MAX_STAGE, stage));
    }

    /** Normalized copy with all three stages present as clamped ints. */
    static Map<String, Object> normalize(Map<String, Object> survival) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hunger", stageOf(survival, "hunger"));
        out.put("thirst", stageOf(survival, "thirst"));
        out.put("fatigue", stageOf(survival, "fatigue"));
        return out;
    }

    static int stageOf(Map<String, Object> survival, String key) {
        Object value = survival.get(key);
        return clamp(value instanceof Number n ? n.intValue() : 0);
    }

    /** One condition bumped by {@code delta} (either sign), clamped. */
    static Map<String, Object> bump(Map<String, Object> survival, String key, int delta) {
        Map<String, Object> out = normalize(survival);
        out.put(key, clamp((int) out.get(key) + delta));
        return out;
    }

    /**
     * The book's time-of-day table. Dawn: +1 hunger, +1 thirst. Noon: +1 fatigue.
     * Dusk: +1 to all three. Night (or anything unknown): no change.
     */
    static Map<String, Object> applyTimeBump(Map<String, Object> survival, String timeOfDay) {
        Map<String, Object> out = normalize(survival);
        switch (timeOfDay == null ? "" : timeOfDay) {
            case "dawn" -> {
                out.put("hunger", clamp((int) out.get("hunger") + 1));
                out.put("thirst", clamp((int) out.get("thirst") + 1));
            }
            case "noon" -> out.put("fatigue", clamp((int) out.get("fatigue") + 1));
            case "dusk" -> {
                out.put("hunger", clamp((int) out.get("hunger") + 1));
                out.put("thirst", clamp((int) out.get("thirst") + 1));
                out.put("fatigue", clamp((int) out.get("fatigue") + 1));
            }
            default -> { /* night: the sleep window — no bumps */ }
        }
        return out;
    }

    /**
     * A player's improvement action: eat a ration (−1 hunger), drink (−1 thirst),
     * a good night's sleep (−3 fatigue) or a disturbed one (−1 fatigue).
     */
    static Map<String, Object> applyAction(Map<String, Object> survival, SurvivalAction action) {
        return switch (action) {
            case EAT -> bump(survival, "hunger", -1);
            case DRINK -> bump(survival, "thirst", -1);
            case SLEEP_GOOD -> bump(survival, "fatigue", -3);
            case SLEEP_DISTURBED -> bump(survival, "fatigue", -1);
        };
    }
}
