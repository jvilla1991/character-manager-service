package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.progression.SubclassChoices;
import com.moo.charactermanagerservice.progression.SubclassChoices.Change;
import com.moo.charactermanagerservice.progression.SubclassChoices.Choices;
import com.moo.charactermanagerservice.progression.SubclassChoices.InvalidChoice;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The stored half of a character's subclass choices: {@code pc.subclass_choices} (V65), and the
 * skill proficiencies in {@code pc.skills} that a skill choice brings with it. The rules
 * themselves (what may be chosen, when a choice is complete) are data and pure functions in
 * {@link SubclassChoices}; this class applies their outcome to a character and turns a broken
 * rule into a 400.
 *
 * <p>Three write paths, and no other:
 * <ul>
 *   <li>a level-up ({@link #applyAtLevelUp}),</li>
 *   <li>the subclass-choices endpoint ({@link #applyDirect}),</li>
 *   <li>a whole-sheet save, which never takes the body's value: it keeps the stored one, fitted
 *       to the class, subclass and level being saved ({@link #keep}).</li>
 * </ul>
 */
final class SubclassChoiceRules {

    private SubclassChoiceRules() {}

    private static final PcJsonColumns JSON = new PcJsonColumns(new ObjectMapper());

    /** The value {@code pc.skills} holds for a proficiency, and for a doubled one. */
    private static final String PROFICIENT = "prof";
    private static final String EXPERT = "expert";

    private static int levelOf(PC pc) {
        return pc.getLevel() == null ? 1 : pc.getLevel();
    }

    /** Run a rule, answering a broken one with a 400 that carries its message. */
    private static <T> T checked(Supplier<T> rule) {
        try {
            return rule.get();
        } catch (InvalidChoice e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * The value to store when a whole-character save arrives (owner update, DM update): the
     * STORED choices, never the body's, with every key the saved class, subclass and level no
     * longer has dropped. Null when nothing is left, so changing the subclass clears them.
     */
    static String keep(PC incoming, PC existing) {
        return SubclassChoices.clean(existing == null ? null : existing.getSubclassChoices(),
                incoming.getClazz(), incoming.getSubclass(), levelOf(incoming));
    }

    /** The skills the character is proficient in (or has Expertise in), as the sheet names them. */
    static List<String> proficientSkills(PC pc) {
        List<String> skills = new ArrayList<>();
        JSON.parseObject(pc.getSkills()).forEach((skill, value) -> {
            if (PROFICIENT.equals(value) || EXPERT.equals(value)) skills.add(skill);
        });
        return skills;
    }

    /**
     * The choices of a level-up, for the character as it will be at {@code newLevel} (its level
     * is not raised yet; its subclass is already the one this level-up chose). A character with
     * no choices that sends none is not touched at all.
     *
     * @param sent the request's {@code subclassChoices}, or null
     */
    static void applyAtLevelUp(PC pc, int newLevel, Map<String, Object> sent) {
        Choices request = checked(() -> SubclassChoices.fromRequest(sent));
        if (request.isEmpty()
                && SubclassChoices.keysFor(pc.getClazz(), pc.getSubclass(), newLevel).isEmpty()) {
            return;
        }
        Change change = checked(() -> SubclassChoices.atLevelUp(pc.getClazz(), pc.getSubclass(), newLevel,
                SubclassChoices.parse(pc.getSubclassChoices()), request, proficientSkills(pc)));
        store(pc, change);
    }

    /**
     * The choices of a direct edit, for the character at its current level. Keys sent replace the
     * stored ones. Returns what was set, in words, for the activity log.
     *
     * @param sent         the request body: a partial choices object with at least one key
     * @param unrestricted true for the campaign's DM and for the owner of a character in no
     *                     campaign, who may set any key. Otherwise (the owning player of a
     *                     character in a campaign) the Vestige type may always be set, and any
     *                     other key only while its stored value is missing or short of what the
     *                     level asks for; a key that is already complete is a 403.
     */
    static List<String> applyDirect(PC pc, Map<String, Object> sent, boolean unrestricted) {
        Choices request = checked(() -> SubclassChoices.fromRequest(sent));
        if (request.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No subclass choice was sent");
        }
        int level = levelOf(pc);
        Choices stored = SubclassChoices.parse(pc.getSubclassChoices());
        checked(() -> {
            SubclassChoices.assertApplicable(request, pc.getClazz(), pc.getSubclass(), level);
            return null;
        });
        if (!unrestricted) {
            for (String key : request.keys()) {
                if (!SubclassChoices.VESTIGE_TYPE.equals(key)
                        && SubclassChoices.isComplete(key, stored, pc.getClazz(), pc.getSubclass(), level)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "In a campaign, only your Dungeon Master can change the " + label(key)
                                    + " once chosen");
                }
            }
        }
        Change change = checked(() -> SubclassChoices.direct(pc.getClazz(), pc.getSubclass(), level,
                stored, request, proficientSkills(pc)));
        store(pc, change);

        List<String> set = new ArrayList<>();
        Map<String, Object> now = change.choices().toMap();
        for (String key : request.keys()) {
            Object value = now.get(key);
            String text = value instanceof List<?> list
                    ? (list.isEmpty() ? "none" : String.join(", ", list.stream().map(String::valueOf).toList()))
                    : String.valueOf(value);
            set.add(label(key) + ": " + text);
        }
        return set;
    }

    /** What a key is called in a sentence. */
    private static String label(String key) {
        return switch (key) {
            case SubclassChoices.SKILLS -> "subclass skills";
            case SubclassChoices.ARCANE_SHOTS -> "Arcane Shot options";
            case SubclassChoices.VESTIGE_TYPE -> "Vestige type";
            case SubclassChoices.VESTIGE_DOMAIN -> "Vestige domain";
            default -> key;
        };
    }

    /**
     * Save a change on the character: the choices, and the skill proficiencies that follow from
     * them. A gained skill is marked proficient unless it already is (Expertise is never lowered
     * to a proficiency); a replaced pick loses the proficiency it brought, but keeps Expertise,
     * which did not come from the pick.
     */
    private static void store(PC pc, Change change) {
        pc.setSubclassChoices(SubclassChoices.write(change.choices()));
        if (change.skillsGained().isEmpty() && change.skillsLost().isEmpty()) return;

        Map<String, Object> skills = JSON.parseObject(pc.getSkills());
        boolean changed = false;
        for (String skill : change.skillsLost()) {
            if (PROFICIENT.equals(skills.get(skill))) {
                skills.remove(skill);
                changed = true;
            }
        }
        for (String skill : change.skillsGained()) {
            Object value = skills.get(skill);
            if (!PROFICIENT.equals(value) && !EXPERT.equals(value)) {
                skills.put(skill, PROFICIENT);
                changed = true;
            }
        }
        if (changed) pc.setSkills(JSON.writeObject(skills));
    }
}
