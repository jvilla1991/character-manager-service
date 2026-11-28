package com.moo.charactermanagerservice.services;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** What a companion's block keeps across an update and across a recompute. */
class CompanionVitalsTest {

    private static Map<String, Object> block(Integer max, Integer cur, Integer temp, List<String> conditions) {
        Map<String, Object> hp = new LinkedHashMap<>();
        hp.put("max", max);
        hp.put("cur", cur);
        hp.put("temp", temp);
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("ac", 17);
        block.put("hp", hp);
        block.put("conditions", conditions);
        block.put("conditionImmunities", List.of("charmed", "frightened", "prone"));
        return block;
    }

    private static Object hp(Map<String, Object> block) {
        return block.get("hp");
    }

    // ── a recompute ──────────────────────────────────────────────────────────────

    @Test
    void carriedOver_raisesCurrentHitPointsByTheGrowthOfTheMaximum() {
        // Warlock 5 -> 6: 24 -> 28. A hurt vestige gains the four, a whole one stays whole.
        assertThat(hp(CompanionVitals.carriedOver(block(28, 28, 0, List.of()), block(24, 9, 0, List.of()))))
                .isEqualTo(Map.of("max", 28, "cur", 13, "temp", 0));
        assertThat(hp(CompanionVitals.carriedOver(block(28, 28, 0, List.of()), block(24, 24, 0, List.of()))))
                .isEqualTo(Map.of("max", 28, "cur", 28, "temp", 0));
        // At 0 Hit Points it gains them too: the rule is the same for every vestige.
        assertThat(hp(CompanionVitals.carriedOver(block(28, 28, 0, List.of()), block(24, 0, 0, List.of()))))
                .isEqualTo(Map.of("max", 28, "cur", 4, "temp", 0));
    }

    @Test
    void carriedOver_anUnchangedOrLowerMaximum_addsNothing_andCapsAtTheNewMaximum() {
        assertThat(hp(CompanionVitals.carriedOver(block(24, 24, 0, List.of()), block(24, 9, 0, List.of()))))
                .isEqualTo(Map.of("max", 24, "cur", 9, "temp", 0));
        // The DM lowered the level: 24 -> 20. A full vestige is capped, a hurt one untouched.
        assertThat(hp(CompanionVitals.carriedOver(block(20, 20, 0, List.of()), block(24, 24, 0, List.of()))))
                .isEqualTo(Map.of("max", 20, "cur", 20, "temp", 0));
        assertThat(hp(CompanionVitals.carriedOver(block(20, 20, 0, List.of()), block(24, 9, 0, List.of()))))
                .isEqualTo(Map.of("max", 20, "cur", 9, "temp", 0));
    }

    @Test
    void carriedOver_keepsTemporaryHitPointsAndConditions_andNothingElseOfTheOldBlock() {
        Map<String, Object> stored = block(24, 10, 7, List.of("poisoned", "stunned"));
        stored.put("ac", 99);
        stored.put("spells", List.of("Wish"));

        Map<String, Object> next = CompanionVitals.carriedOver(block(24, 24, 0, List.of()), stored);

        assertThat(hp(next)).isEqualTo(Map.of("max", 24, "cur", 10, "temp", 7));
        assertThat(next.get("conditions")).isEqualTo(List.of("poisoned", "stunned"));
        assertThat(next.get("ac")).isEqualTo(17);
        assertThat(next).doesNotContainKey("spells");
    }

    @Test
    void carriedOver_aStoredBlockWithoutReadableHitPoints_leavesTheFreshOneFull() {
        Map<String, Object> broken = new LinkedHashMap<>();
        broken.put("hp", "lots");
        assertThat(hp(CompanionVitals.carriedOver(block(24, 24, 0, List.of()), broken)))
                .isEqualTo(Map.of("max", 24, "cur", 24, "temp", 0));
        assertThat(hp(CompanionVitals.carriedOver(block(24, 24, 0, List.of()), new LinkedHashMap<>())))
                .isEqualTo(Map.of("max", 24, "cur", 24, "temp", 0));
        assertThat(CompanionVitals.carriedOver(block(24, 24, 0, List.of()), new LinkedHashMap<>())
                .get("conditions")).isEqualTo(List.of());
    }

    @Test
    void carriedOver_doesNotChangeTheBlocksItIsGiven() {
        Map<String, Object> fresh = block(28, 28, 0, List.of());
        Map<String, Object> stored = block(24, 9, 2, List.of("poisoned"));
        CompanionVitals.carriedOver(fresh, stored);

        assertThat(hp(fresh)).isEqualTo(Map.of("max", 28, "cur", 28, "temp", 0));
        assertThat(fresh.get("conditions")).isEqualTo(List.of());
        assertThat(hp(stored)).isEqualTo(Map.of("max", 24, "cur", 9, "temp", 2));
    }

    // ── an update ────────────────────────────────────────────────────────────────

    @Test
    void edited_takesTheThreeValues_cleaned_andLeavesTheRestOfTheStoredBlock() {
        Map<String, Object> request = block(999, 40, -2,
                Arrays.asList("Charmed", "poisoned", "POISONED", null, "x".repeat(41), "restrained"));
        request.put("ac", 1);
        request.put("conditionImmunities", List.of());

        Map<String, Object> next = CompanionVitals.edited(block(24, 24, 0, List.of()), request);

        assertThat(hp(next)).isEqualTo(Map.of("max", 24, "cur", 24, "temp", 0));
        assertThat(next.get("conditions")).isEqualTo(List.of("poisoned", "restrained"));
        assertThat(next.get("ac")).isEqualTo(17);
        assertThat(next.get("conditionImmunities")).isEqualTo(List.of("charmed", "frightened", "prone"));
    }

    @Test
    void edited_neverKeepsMoreConditionsThanTheCap() {
        List<String> many = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) many.add("condition " + i);
        Map<String, Object> next = CompanionVitals.edited(block(24, 24, 0, List.of()), Map.of("conditions", many));
        assertThat((List<?>) next.get("conditions")).hasSize(CompanionVitals.MAX_CONDITIONS);
    }
}
