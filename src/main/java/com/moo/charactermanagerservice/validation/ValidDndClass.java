package com.moo.charactermanagerservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = DndClassValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDndClass {
    String message() default "must be a valid 2024 PHB class " +
            "(Barbarian, Bard, Cleric, Druid, Fighter, Monk, Paladin, Ranger, Rogue, Sorcerer, Warlock, or Wizard)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
