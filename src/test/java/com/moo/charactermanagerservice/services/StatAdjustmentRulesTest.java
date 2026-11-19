package com.moo.charactermanagerservice.services;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Shape rules for the DM's adjustment lines on calculated numbers (V57). */
class StatAdjustmentRulesTest {

    // --- normalizeJson -------------------------------------------------------

    @Test
    void normalizeJson_keepsAWellFormedValue_andTrimsLabels() {
        String stored = StatAdjustmentRules.normalizeJson("{\"ac\":[{\"value\":1,\"label\":\"  Taunka \"}]}");

        assertThat(stored).isEqualTo("{\"ac\":[{\"value\":1,\"label\":\"Taunka\"}]}");
    }

    @Test
    void normalizeJson_returnsNull_whenTheBodyCarriedNone() {
        assertThat(StatAdjustmentRules.normalizeJson(null)).isNull();
        assertThat(StatAdjustmentRules.normalizeJson("  ")).isNull();
        assertThat(StatAdjustmentRules.normalizeJson("null")).isNull();
    }

    @Test
    void normalizeJson_dropsANumberWhoseLinesWereAllRemoved() {
        assertThat(StatAdjustmentRules.normalizeJson("{\"ac\":[]}")).isEqualTo("{}");
    }

    @Test
    void normalizeJson_acceptsNegativeLines_andNumbersBeyondArmorClass() {
        String json = "{\"ac\":[{\"value\":-2,\"label\":\"Cursed\"}],"
                + "\"STR\":[{\"value\":1,\"label\":\"Level 4 Increase\"}]}";

        assertThat(StatAdjustmentRules.normalizeJson(json)).isEqualTo(json);
    }

    /** A value with `count` different keys, one line each. */
    private static String withKeys(int count) {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < count; i++) {
            if (i > 0) json.append(',');
            json.append("\"stat").append(i).append("\":[{\"value\":1,\"label\":\"Line\"}]");
        }
        return json.append('}').toString();
    }

    @Test
    void normalizeJson_holdsEveryNumberWithABreakdownAtOnce() {
        // Armor Class and its removed lines, six ability scores, nine spell slot levels.
        assertThat(StatAdjustmentRules.STATS_MAX).isGreaterThanOrEqualTo(2 + 6 + 9);

        String json = withKeys(StatAdjustmentRules.STATS_MAX);
        assertThat(StatAdjustmentRules.normalizeJson(json)).isEqualTo(json);
        assertThat(StatAdjustmentRules.STATS_MAX).isEqualTo(32);
    }

    @Test
    void normalizeJson_rejectsMoreNumbersThanTheLimit() {
        assertBadRequest(withKeys(StatAdjustmentRules.STATS_MAX + 1));
    }

    @Test
    void normalizeJson_dropsFieldsItDoesNotKnow() {
        String stored = StatAdjustmentRules.normalizeJson(
                "{\"ac\":[{\"value\":1,\"label\":\"Taunka\",\"hidden\":true}]}");

        assertThat(stored).isEqualTo("{\"ac\":[{\"value\":1,\"label\":\"Taunka\"}]}");
    }

    @Test
    void normalizeJson_rejectsAnythingThatIsNotAnObjectOfLineLists() {
        assertBadRequest("not json");
        assertBadRequest("[1,2]");
        assertBadRequest("{\"ac\":5}");
        assertBadRequest("{\"ac\":[\"+1 Taunka\"]}");
        assertBadRequest("{\"armor class!\":[]}");
    }

    @Test
    void normalizeJson_rejectsALineWithoutAWholeNonZeroValueInRange() {
        assertBadRequest("{\"ac\":[{\"label\":\"Taunka\"}]}");
        assertBadRequest("{\"ac\":[{\"value\":0,\"label\":\"Taunka\"}]}");
        assertBadRequest("{\"ac\":[{\"value\":1.5,\"label\":\"Taunka\"}]}");
        assertBadRequest("{\"ac\":[{\"value\":\"1\",\"label\":\"Taunka\"}]}");
        assertBadRequest("{\"ac\":[{\"value\":100,\"label\":\"Taunka\"}]}");
        assertBadRequest("{\"ac\":[{\"value\":-100,\"label\":\"Taunka\"}]}");
    }

    @Test
    void normalizeJson_rejectsALineWithoutAUsableLabel() {
        String tooLong = "x".repeat(StatAdjustmentRules.LABEL_MAX + 1);

        assertBadRequest("{\"ac\":[{\"value\":1}]}");
        assertBadRequest("{\"ac\":[{\"value\":1,\"label\":\"   \"}]}");
        assertBadRequest("{\"ac\":[{\"value\":1,\"label\":\"" + tooLong + "\"}]}");
    }

    @Test
    void normalizeJson_rejectsMoreLinesThanTheLimit() {
        String line = "{\"value\":1,\"label\":\"Stack\"}";
        String lines = String.join(",", Collections.nCopies(StatAdjustmentRules.LINES_MAX + 1, line));

        assertBadRequest("{\"ac\":[" + lines + "]}");
    }

    private void assertBadRequest(String json) {
        assertThatThrownBy(() -> StatAdjustmentRules.normalizeJson(json))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- companion key for removed calculated lines ---------------------------

    @Test
    void removedKey_isTheNumbersNamePlusRemoved_andReadsBack() {
        assertThat(StatAdjustmentRules.removedKey("ac")).isEqualTo("acRemoved");
        assertThat(StatAdjustmentRules.isRemovedKey("acRemoved")).isTrue();
        assertThat(StatAdjustmentRules.statOfRemovedKey("acRemoved")).isEqualTo("ac");
        assertThat(StatAdjustmentRules.isRemovedKey("ac")).isFalse();
        assertThat(StatAdjustmentRules.isRemovedKey("Removed")).isFalse(); // no number in front of it
        assertThat(StatAdjustmentRules.isRemovedKey(null)).isFalse();
    }

    @Test
    void isRemovedKey_neverTakesARealNumberForACompanionKey() {
        // Every key that is a number in its own right: Armor Class, the ability scores, the
        // bonus spell slots of each spell level, and the shapes the key pattern was written for.
        List<String> numbers = new java.util.ArrayList<>(
                List.of("ac", "STR", "DEX", "CON", "INT", "WIS", "CHA", "hpMax", "speed", "removed", "acremoved"));
        for (int spellLevel = 1; spellLevel <= 9; spellLevel++) numbers.add(SpellSlotState.bonusKey(spellLevel));

        assertThat(numbers).noneMatch(StatAdjustmentRules::isRemovedKey);
        // A slot level's own companion key reads back to that slot level, not to anything shorter.
        assertThat(StatAdjustmentRules.statOfRemovedKey(StatAdjustmentRules.removedKey("slot3"))).isEqualTo("slot3");
    }

    @Test
    void normalizeJson_storesRemovedLinesBesideTheAdjustments() {
        String json = "{\"ac\":[{\"value\":1,\"label\":\"Taunka\"}],"
                + "\"acRemoved\":[{\"value\":1,\"label\":\"Dexterity\"}]}";

        assertThat(StatAdjustmentRules.normalizeJson(json)).isEqualTo(json);
    }

    // --- parse ---------------------------------------------------------------

    @Test
    void parse_readsLinesPerNumber() {
        Map<String, List<StatAdjustmentRules.Line>> parsed = StatAdjustmentRules.parse(
                "{\"ac\":[{\"value\":1,\"label\":\"Taunka\"},{\"value\":-1,\"label\":\"Cursed\"}]}");

        assertThat(parsed).containsOnlyKeys("ac");
        assertThat(parsed.get("ac")).containsExactly(
                new StatAdjustmentRules.Line(1, "Taunka"), new StatAdjustmentRules.Line(-1, "Cursed"));
        assertThat(parsed.get("ac").get(0).describe()).isEqualTo("+1 Taunka");
        assertThat(parsed.get("ac").get(1).describe()).isEqualTo("-1 Cursed");
    }

    @Test
    void parse_neverThrows_onNullBlankOrMalformedText() {
        assertThat(StatAdjustmentRules.parse(null)).isEmpty();
        assertThat(StatAdjustmentRules.parse("")).isEmpty();
        assertThat(StatAdjustmentRules.parse("not json")).isEmpty();
        assertThat(StatAdjustmentRules.parse("{\"ac\":5}")).isEmpty();
        assertThat(StatAdjustmentRules.parse("{\"ac\":[{\"value\":\"x\"}]}")).isEmpty();
    }
}
