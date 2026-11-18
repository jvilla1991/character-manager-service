package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.progression.ClassProgression;
import com.moo.charactermanagerservice.progression.ClassProgression.ClassSpecializations;
import com.moo.charactermanagerservice.progression.ClassProgression.HeroSubclass;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Request validation for World of Warcraft tabletop-module class options, in two shapes:
 * <ul>
 *   <li>"hero classes" — e.g. the Death Knight (clazz {@code paladin} + subclass
 *       {@value ClassProgression#EBON_BLADE}) and the Demon Hunter (clazz {@code ranger} + subclass
 *       {@value ClassProgression#DEMON_HUNTER}) — whose {@code specialization} is drawn from the
 *       hero subclass's own list;</li>
 *   <li>WoW class variants with no subclass lock — e.g. the WoW Druid (clazz {@code druid}, any
 *       normal circle), the WoW Paladin (clazz {@code paladin}, Holy/Protection/Retribution), the
 *       WoW Rogue (clazz {@code rogue}, Assassination/Outlaw/Subtlety) and the WoW Shaman (clazz
 *       {@code barbarian}, Elemental/Enhancement/Restoration) —
 *       whose {@code specialization} is drawn from the class's list. A druid or paladin with no
 *       specialization is a base-game one. A paladin with the Ebon Blade hero subclass is a Death
 *       Knight: the hero subclass's list wins, so it can't take "Holy" (nor a WoW paladin "Frost").</li>
 * </ul>
 * The rules themselves (lists, base classes, respec policy) are data in {@link ClassProgression};
 * this class turns violations into 400s for the PC write paths (create, owner update, DM update)
 * and the level-up specialization choice.
 */
final class HeroClassRules {

    private HeroClassRules() {}

    /** The specialization list + respec policy that applies to one PC, whichever shape it is. */
    private record SpecializationRule(String label, List<String> specializations, boolean respecOnLevelUp) {}

    /**
     * A hero subclass's list wins; otherwise the class's WoW specialization list, if any. Empty for
     * a PC that cannot have a specialization at all.
     */
    private static Optional<SpecializationRule> ruleFor(String clazz, String subclass) {
        Optional<HeroSubclass> hero = ClassProgression.heroSubclass(subclass);
        if (hero.isPresent()) {
            HeroSubclass h = hero.get();
            return Optional.of(new SpecializationRule(h.name(), h.specializations(), h.respecOnLevelUp()));
        }
        return ClassProgression.classSpecializations(clazz).map(HeroClassRules::ruleOf);
    }

    private static SpecializationRule ruleOf(ClassSpecializations s) {
        return new SpecializationRule(s.baseClass(), s.specializations(), s.respecOnLevelUp());
    }

    /**
     * Validate a PC body's WoW fields: a hero subclass must belong to the PC's class, and any
     * specialization must be valid for the hero subclass (or, without one, for the class). Other
     * (non-hero) subclass values are left alone, as before.
     */
    static void validate(PC pc) {
        String subclass = pc.getSubclass();
        if (ClassProgression.isHeroSubclass(subclass)
                && !ClassProgression.heroSubclassAllowedFor(subclass, pc.getClazz())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'" + subclass.trim() + "' is not available to " + pc.getClazz());
        }
        validateSpecialization(pc.getClazz(), subclass, pc.getSpecialization());
    }

    /**
     * A specialization may be null; a non-null one must be exactly (case-sensitively) one of the
     * hero subclass's specializations, or — with no hero subclass — one of the class's WoW
     * specializations. Anything else (including a specialization on a class with no WoW variant)
     * is a 400.
     */
    static void validateSpecialization(String clazz, String subclass, String specialization) {
        if (specialization == null) return;
        SpecializationRule rule = ruleFor(clazz, subclass).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A specialization can only be set on a hero subclass character or a class with"
                                + " World of Warcraft specializations"));
        if (!rule.specializations().contains(specialization)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'" + specialization + "' is not a valid " + rule.label() + " specialization (expected one of "
                            + String.join(", ", rule.specializations()) + ")");
        }
    }

    /**
     * A specialization chosen on level-up: validated as above, and — when the rule does not allow
     * respec on level-up (e.g. the Demon Hunter) — only accepted while none is set yet (or when it
     * repeats the current one). {@code null} means "keep the current one" and is always fine.
     */
    static void validateLevelUpSpecialization(String clazz, String subclass, String current, String requested) {
        if (requested == null) return;
        validateSpecialization(clazz, subclass, requested);
        SpecializationRule rule = ruleFor(clazz, subclass).orElseThrow();
        if (!rule.respecOnLevelUp() && current != null && !current.equals(requested)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A " + rule.label() + " specialization cannot be changed on level-up");
        }
    }

    /**
     * Owner update: for a hero subclass without respec on level-up, an already-chosen
     * specialization is locked for the player — only the DM update path may change it. Runs after
     * the stored value has been carried over onto a body that omitted the field. WoW class
     * specializations (e.g. the druid's) all allow respec, so the owner may change them.
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
