package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.progression.ClassProgression;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Request validation for "hero classes" (World of Warcraft tabletop module) — e.g. the Death
 * Knight, stored as clazz {@code paladin} + subclass {@value ClassProgression#EBON_BLADE} plus a
 * {@code rune} specialization. The rules themselves (which subclasses are hero subclasses, which
 * runes exist) live in {@link ClassProgression}; this class turns violations into 400s for the
 * PC write paths (create, owner update, DM update) and the level-up rune swap.
 */
final class HeroClassRules {

    private HeroClassRules() {}

    /**
     * Validate a PC body's hero-class fields: a hero subclass must belong to the PC's class, and
     * any rune must be valid for the subclass. Other (non-hero) subclass values are left alone,
     * as before.
     */
    static void validate(PC pc) {
        String subclass = pc.getSubclass();
        if (ClassProgression.isHeroSubclass(subclass)
                && !ClassProgression.heroSubclassAllowedFor(subclass, pc.getClazz())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'" + subclass.trim() + "' is not available to " + pc.getClazz());
        }
        validateRune(subclass, pc.getRune());
    }

    /**
     * A rune may be null; a non-null rune must be exactly one of {@link ClassProgression#RUNES}
     * and is only allowed on an {@value ClassProgression#EBON_BLADE} character.
     */
    static void validateRune(String subclass, String rune) {
        if (rune == null) return;
        if (!ClassProgression.isValidRune(rune)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'" + rune + "' is not a valid rune (expected one of "
                            + String.join(", ", ClassProgression.RUNES) + ")");
        }
        if (!ClassProgression.isEbonBlade(subclass)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A rune can only be set on an " + ClassProgression.EBON_BLADE + " character");
        }
    }
}
