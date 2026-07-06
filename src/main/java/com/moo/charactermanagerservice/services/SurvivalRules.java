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
    // A never-tracked condition sits at "Ok" — the table's neutral row. Stage 0
    // is "Stuffed" (better than well-fed, worth −1 exhaustion), which a fresh
    // character hasn't earned.
    static final int DEFAULT_STAGE = 2;

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
        return clamp(value instanceof Number n ? n.intValue() : DEFAULT_STAGE);
    }

    /** One condition bumped by {@code delta} (either sign), clamped. */
    static Map<String, Object> bump(Map<String, Object> survival, String key, int delta) {
        Map<String, Object> out = normalize(survival);
        out.put(key, clamp((int) out.get(key) + delta));
        return out;
    }

    /**
     * Apply one day-segment under the auto-consume model. Time worsens the
     * party per the book's three-segment table (morning +hunger +thirst, noon
     * +fatigue, night +all three), but the characters automatically eat/drink
     * their supplies: hunger and thirst rise ONLY when the ration/waterskin ran
     * out ({@code ate}/{@code drank} false). Fatigue has no resource and always
     * rises on its segments. Extra keys on the map (e.g. {@code seeded}) are
     * preserved.
     */
    static Map<String, Object> applySegment(Map<String, Object> survival, String segment,
                                            boolean ate, boolean drank) {
        Map<String, Object> out = new LinkedHashMap<>(survival);
        int hunger = stageOf(survival, "hunger");
        int thirst = stageOf(survival, "thirst");
        int fatigue = stageOf(survival, "fatigue");

        boolean htStep = "morning".equals(segment) || "night".equals(segment);
        boolean fatigueStep = "noon".equals(segment) || "night".equals(segment);
        if (htStep && !ate) hunger = clamp(hunger + 1);
        if (htStep && !drank) thirst = clamp(thirst + 1);
        if (fatigueStep) fatigue = clamp(fatigue + 1);

        out.put("hunger", hunger);
        out.put("thirst", thirst);
        out.put("fatigue", fatigue);
        return out;
    }

    /** Whether this day-segment consumes rations/water (a hunger/thirst step). */
    static boolean isSupplyStep(String segment) {
        return "morning".equals(segment) || "night".equals(segment);
    }
}
