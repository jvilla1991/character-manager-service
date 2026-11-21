package com.moo.charactermanagerservice.services;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionRulesTest {

    @Test
    void normalize_keepsNullAsNull() {
        assertThat(ConditionRules.normalizeImmunitiesJson(null, "[\"prone\"]")).isNull();
    }

    @Test
    void normalize_trimsAndDropsBlanksAndRepeats() {
        String out = ConditionRules.normalizeImmunitiesJson("[\" poisoned \",\"\",\"charmed\",\"poisoned\"]", null);

        assertThat(out).isEqualTo("[\"poisoned\",\"charmed\"]");
    }

    @Test
    void normalize_dropsAnImmunityToAnActiveCondition() {
        String out = ConditionRules.normalizeImmunitiesJson("[\"poisoned\",\"charmed\"]", "[\"poisoned\",\"prone\"]");

        assertThat(out).isEqualTo("[\"charmed\"]");
    }

    @Test
    void normalize_readsABlankBodyAsNoImmunities() {
        assertThat(ConditionRules.normalizeImmunitiesJson("  ", null)).isEqualTo("[]");
    }

    @Test
    void normalize_ignoresMalformedStoredConditions() {
        assertThat(ConditionRules.normalizeImmunitiesJson("[\"charmed\"]", "not json")).isEqualTo("[\"charmed\"]");
    }

    @Test
    void normalize_refusesSomethingThatIsNotAnArrayOfNames() {
        assertThatThrownBy(() -> ConditionRules.normalizeImmunitiesJson("{\"poisoned\":true}", null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ConditionRules.normalizeImmunitiesJson("[1,2]", null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ConditionRules.normalizeImmunitiesJson("[\"poisoned\"", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void normalize_refusesTooManyOrTooLongNames() {
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i <= ConditionRules.NAMES_MAX; i++) {
            many.append(i == 0 ? "" : ",").append("\"c").append(i).append('"');
        }
        many.append(']');
        assertThatThrownBy(() -> ConditionRules.normalizeImmunitiesJson(many.toString(), null))
                .isInstanceOf(ResponseStatusException.class);

        String longName = "[\"" + "x".repeat(ConditionRules.NAME_MAX + 1) + "\"]";
        assertThatThrownBy(() -> ConditionRules.normalizeImmunitiesJson(longName, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void sameNames_ignoresOrder_andReadsNullAsNone() {
        assertThat(ConditionRules.sameNames("[\"a\",\"b\"]", "[\"b\",\"a\"]")).isTrue();
        assertThat(ConditionRules.sameNames(null, "[]")).isTrue();
        assertThat(ConditionRules.sameNames(null, "[\"a\"]")).isFalse();
    }
}
