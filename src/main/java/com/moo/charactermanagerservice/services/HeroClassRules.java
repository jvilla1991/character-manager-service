package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.progression.ClassProgression;
import com.moo.charactermanagerservice.progression.ClassProgression.HeroSubclass;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * Request validation for "hero classes" (World of Warcraft tabletop module) — e.g. the Death
 * Knight (clazz {@code paladin} + subclass {@value ClassProgression#EBON_BLADE}) and the Demon
 * Hunter (clazz {@code ranger} + subclass {@value ClassProgression#DEMON_HUNTER}), each with a
 * {@code specialization} drawn from its own list. The rules themselves (which subclasses are hero
 * subclasses, their base class, allowed specializations and respec policy) are data in
 * {@link ClassProgression}; this class turns violations into 400s for the PC write paths (create,
 * owner update, DM update) and the level-up specialization choice.
 */
final class HeroClassRules {

    private HeroClassRules() {}

    /**
     * Validate a PC body's hero-class fields: a hero subclass must belong to the PC's class, and
     * any specialization must be valid for the subclass. Other (non-hero) subclass values are left
     * alone, as before.
     */
    static void validate(PC pc) {
        String subclass = pc.getSubclass();
        if (ClassProgression.isHeroSubclass(subclass)
                && !ClassProgression.heroSubclassAllowedFor(subclass, pc.getClazz())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'" + subclass.trim() + "' is not available to " + pc.getClazz());
        }
        validateSpecialization(subclass, pc.getSpecialization());
    }

    /**
     * A specialization may be null; a non-null one is only allowed on a hero subclass and must be
     * exactly (case-sensitively) one of that subclass's specializations.
     */
    static void validateSpecialization(String subclass, String specialization) {
        if (specialization == null) return;
        HeroSubclass hero = ClassProgression.heroSubclass(subclass).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A specialization can only be set on a hero subclass character"));
        if (!hero.specializations().contains(specialization)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'" + specialization + "' is not a valid " + hero.name() + " specialization (expected one of "
                            + String.join(", ", hero.specializations()) + ")");
        }
    }

    /**
     * A specialization chosen on level-up: validated as above, and — when the hero subclass does
     * not allow respec on level-up — only accepted while none is set yet (or when it repeats the
     * current one). {@code null} means "keep the current one" and is always fine.
     */
    static void validateLevelUpSpecialization(String subclass, String current, String requested) {
        if (requested == null) return;
        validateSpecialization(subclass, requested);
        HeroSubclass hero = ClassProgression.heroSubclass(subclass).orElseThrow();
        if (!hero.respecOnLevelUp() && current != null && !current.equals(requested)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A " + hero.name() + " specialization cannot be changed on level-up");
        }
    }

    /**
     * Owner update: for a hero subclass without respec on level-up, an already-chosen
     * specialization is locked for the player — only the DM update path may change it. Runs after
     * the stored value has been carried over onto a body that omitted the field.
     */
    static void assertOwnerMayChangeSpecialization(PC existing, PC incoming) {
        HeroSubclass hero = ClassProgression.heroSubclass(incoming.getSubclass()).orElse(null);
        if (hero == null || hero.respecOnLevelUp()) return;
        boolean sameSubclass = existing.getSubclass() != null
                && hero.name().equalsIgnoreCase(existing.getSubclass().trim());
        if (sameSubclass && existing.getSpecialization() != null
                && !Objects.equals(existing.getSpecialization(), incoming.getSpecialization())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A " + hero.name() + " specialization can only be changed by the DM");
        }
    }
}
