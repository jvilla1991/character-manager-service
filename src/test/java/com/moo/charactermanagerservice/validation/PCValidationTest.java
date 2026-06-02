package com.moo.charactermanagerservice.validation;

import com.moo.charactermanagerservice.models.PC;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates Bean Validation constraints on PC for the OnCreate group without spinning
 * up a Spring context — uses the Jakarta Validator API directly.
 */
class PCValidationTest {

    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Returns a PC that satisfies all OnCreate constraints. */
    private PC validPc() {
        PC pc = new PC();
        pc.setName("Aelindra");
        pc.setClazz("Wizard");
        pc.setAbilityStr((short) 10);
        pc.setAbilityDex((short) 14);
        pc.setAbilityCon((short) 12);
        pc.setAbilityInt((short) 16);
        pc.setAbilityWis((short) 11);
        pc.setAbilityCha((short) 8);
        return pc;
    }

    private Set<ConstraintViolation<PC>> validate(PC pc) {
        return validator.validate(pc, ValidationGroups.OnCreate.class);
    }

    private boolean hasViolationOnField(Set<ConstraintViolation<PC>> violations, String field) {
        return violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(field));
    }

    // ── Baseline ──────────────────────────────────────────────────────────────

    @Test
    void validPc_passesAllConstraints() {
        assertThat(validate(validPc())).isEmpty();
    }

    // ── name ─────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankName_failsValidation(String name) {
        PC pc = validPc();
        pc.setName(name);
        assertThat(hasViolationOnField(validate(pc), "name")).isTrue();
    }

    // ── clazz ─────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankClass_failsValidation(String clazz) {
        PC pc = validPc();
        pc.setClazz(clazz);
        assertThat(hasViolationOnField(validate(pc), "clazz")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Commoner", "Fighter2", "dragon", "abc", "123"})
    void invalidClassName_failsValidation(String clazz) {
        PC pc = validPc();
        pc.setClazz(clazz);
        assertThat(hasViolationOnField(validate(pc), "clazz")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Barbarian", "Bard", "Cleric", "Druid", "Fighter",
            "Monk", "Paladin", "Ranger", "Rogue", "Sorcerer", "Warlock", "Wizard",
            "barbarian", "WIZARD", "Rogue"
    })
    void validClassName_passesValidation(String clazz) {
        PC pc = validPc();
        pc.setClazz(clazz);
        assertThat(hasViolationOnField(validate(pc), "clazz")).isFalse();
    }

    // ── ability scores ────────────────────────────────────────────────────────

    @Test
    void nullAbilityScore_failsValidation() {
        PC pc = validPc();
        pc.setAbilityStr(null);
        assertThat(hasViolationOnField(validate(pc), "abilityStr")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(shorts = {0, 1, 2, -1, -10})
    void abilityScoreBelowMin_failsValidation(short score) {
        PC pc = validPc();
        pc.setAbilityDex(score);
        assertThat(hasViolationOnField(validate(pc), "abilityDex")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(shorts = {19, 20, 30, 100})
    void abilityScoreAboveMax_failsValidation(short score) {
        PC pc = validPc();
        pc.setAbilityCon(score);
        assertThat(hasViolationOnField(validate(pc), "abilityCon")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(shorts = {3, 8, 10, 14, 17, 18})
    void abilityScoreInRange_passesValidation(short score) {
        PC pc = validPc();
        pc.setAbilityInt(score);
        pc.setAbilityWis(score);
        pc.setAbilityCha(score);
        assertThat(validate(pc)).isEmpty();
    }

    // ── all six scores must be present ────────────────────────────────────────

    @Test
    void missingAllAbilityScores_produceSixViolations() {
        PC pc = validPc();
        pc.setAbilityStr(null);
        pc.setAbilityDex(null);
        pc.setAbilityCon(null);
        pc.setAbilityInt(null);
        pc.setAbilityWis(null);
        pc.setAbilityCha(null);

        Set<ConstraintViolation<PC>> violations = validate(pc);
        long scoreViolations = violations.stream()
                .filter(v -> v.getPropertyPath().toString().startsWith("ability"))
                .count();
        assertThat(scoreViolations).isEqualTo(6);
    }

    // ── OnCreate group isolation ──────────────────────────────────────────────

    @Test
    void constraints_notTriggered_outsideOnCreateGroup() {
        PC pc = new PC(); // everything null — would fail OnCreate
        Set<ConstraintViolation<PC>> violations = validator.validate(pc); // default group
        assertThat(violations).isEmpty();
    }
}
