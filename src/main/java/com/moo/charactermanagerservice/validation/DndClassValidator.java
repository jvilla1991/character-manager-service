package com.moo.charactermanagerservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public class DndClassValidator implements ConstraintValidator<ValidDndClass, String> {

    static final Set<String> VALID_CLASSES = Set.of(
            "barbarian", "bard", "cleric", "druid", "fighter",
            "monk", "paladin", "ranger", "rogue", "sorcerer", "warlock", "wizard"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null/blank deferred to @NotBlank — composition follows Jakarta convention
        if (value == null || value.isBlank()) return true;
        return VALID_CLASSES.contains(value.strip().toLowerCase());
    }
}
