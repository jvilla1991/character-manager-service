package com.moo.charactermanagerservice.progression;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The choices a subclass lets the player make beyond picking the subclass itself (the Arcana
 * Unleashed subclasses): which choices a character has, what may be chosen, and when a choice is
 * complete. Two kinds:
 * <ul>
 *   <li><b>Choices stored on the character</b> ({@code pc.subclass_choices}, a JSON object with
 *       the keys {@link #KEYS}): the skill proficiencies an Arcana Domain cleric or an Arcane
 *       Archer picks, the Arcane Shot options an Arcane Archer knows, and a Vestige Patron's
 *       companion type and domain.</li>
 *   <li><b>Spells the player picks</b> ({@link #spellPicksFor}): Student of Arcana's two Wizard
 *       cantrips, Magical Mastery's four Wizard spells and Arcane Archer Lore's cantrip. These are
 *       not stored here: they live in the character's spell list, each marked with the feature's
 *       name as its {@code grantedBy}, the way a feat's picks are
 *       ({@link FeatSpellGrants#pickRule}).</li>
 * </ul>
 *
 * <p>Whether a choice MUST be made before a level-up goes through is decided in one place,
 * {@link #REQUIRED}. A choice that is not listed there is checked when it is sent and left alone
 * when it is not.
 *
 * <p>Pure rules: no Spring, no entity. A broken rule is reported as an {@link InvalidChoice} whose
 * message is written for the player; the services turn it into a 400. Class, subclass and option
 * names are matched without regard to case or surrounding spaces, and saved in the spelling used
 * here.
 */
public final class SubclassChoices {

    private SubclassChoices() {}

    // ── The subclasses with choices ──────────────────────────────────────────────

    public static final String ARCANA_DOMAIN = "Arcana Domain";
    public static final String ARCANE_ARCHER = "Arcane Archer";
    public static final String VESTIGE_PATRON = "Vestige Patron";

    /** The level at which all three subclasses gain the features their choices belong to. */
    public static final int CHOICE_LEVEL = 3;

    // ── The stored choices ───────────────────────────────────────────────────────

    public static final String SKILLS = "skills";
    public static final String ARCANE_SHOTS = "arcaneShots";
    public static final String VESTIGE_TYPE = "vestigeType";
    public static final String VESTIGE_DOMAIN = "vestigeDomain";

    /** Every key of the stored object, in the order it is written. */
    public static final List<String> KEYS = List.of(SKILLS, ARCANE_SHOTS, VESTIGE_TYPE, VESTIGE_DOMAIN);

    // ── The features whose spells the player picks ───────────────────────────────

    public static final String STUDENT_OF_ARCANA = "Student of Arcana";
    public static final String MAGICAL_MASTERY = "Magical Mastery";
    public static final String ARCANE_ARCHER_LORE = "Arcane Archer Lore";

    /** The Cleric level at which an Arcana Domain cleric gains Magical Mastery. */
    public static final int MAGICAL_MASTERY_LEVEL = 17;

    /**
     * The choices a level-up refuses to go through without, named by stored key or by spell-pick
     * feature. Everything else is optional for now: valid when sent, accepted when absent. To make
     * one required, add its key ({@link #ARCANE_SHOTS}, {@link #VESTIGE_TYPE},
     * {@link #VESTIGE_DOMAIN}) or its feature ({@link #ARCANE_ARCHER_LORE}) here; nothing else
     * needs to change.
     */
    static final Set<String> REQUIRED = Set.of(SKILLS, STUDENT_OF_ARCANA, MAGICAL_MASTERY);

    /** True when a level-up must leave this stored key or spell-pick feature complete. */
    public static boolean isRequired(String keyOrFeature) {
        return REQUIRED.contains(keyOrFeature);
    }

    // ── What may be chosen ───────────────────────────────────────────────────────

    /** The skills a Cleric chooses from at level 1 (2024 PHB). */
    public static final List<String> CLERIC_SKILLS =
            List.of("History", "Insight", "Medicine", "Persuasion", "Religion");

    /** Student of Arcana: Arcana, or one of the Cleric's level 1 skills. */
    public static final List<String> ARCANA_DOMAIN_SKILLS = List.of(
            "Arcana", "History", "Insight", "Medicine", "Persuasion", "Religion");

    /** The skills a Fighter chooses from at level 1 (2024 PHB): Arcane Archer Lore's replacements. */
    public static final List<String> FIGHTER_SKILLS = List.of(
            "Acrobatics", "Animal Handling", "Athletics", "History", "Insight", "Intimidation",
            "Persuasion", "Perception", "Survival");

    /** The two skills Arcane Archer Lore gives outright. */
    public static final List<String> ARCANE_ARCHER_SKILLS = List.of("Arcana", "Nature");

    /** The Arcane Shot options, in the book's (alphabetical) order. */
    public static final List<String> ARCANE_SHOT_OPTIONS = List.of(
            "Banishing Shot", "Beguiling Shot", "Bursting Shot", "Enfeebling Shot", "Grasping Shot",
            "Piercing Shot", "Seeking Shot", "Shadow Shot");

    /** What a Vestige Patron's companion can be. */
    public static final List<String> VESTIGE_TYPES = List.of("Celestial", "Fiend", "Undead");

    /** The Cleric domains a Vestige Patron's Vestige Spells can come from. */
    public static final List<String> VESTIGE_DOMAINS = List.of("Life", "Light", "Trickery", "War");

    /** The two cantrips Arcane Archer Lore chooses between. */
    public static final List<String> ARCANE_ARCHER_LORE_CANTRIPS = List.of("Druidcraft", "Prestidigitation");

    /**
     * How many Arcane Shot options an Arcane Archer knows at a Fighter level: two from level 3,
     * one more at 7, 10, 15 and 18. None below level 3.
     */
    public static int arcaneShotCount(int fighterLevel) {
        if (fighterLevel < CHOICE_LEVEL) return 0;
        return 2 + (fighterLevel >= 7 ? 1 : 0) + (fighterLevel >= 10 ? 1 : 0)
                + (fighterLevel >= 15 ? 1 : 0) + (fighterLevel >= 18 ? 1 : 0);
    }

    /**
     * The one of the three subclasses this class and subclass name, in the spelling used here, or
     * null. The class counts: "Arcane Archer" on a wizard is not the Fighter subclass.
     */
    public static String subclassWithChoices(String clazz, String subclass) {
        if (clazz == null || subclass == null) return null;
        String c = clazz.trim().toLowerCase(Locale.ROOT);
        String s = subclass.trim();
        if (c.equals("cleric") && ARCANA_DOMAIN.equalsIgnoreCase(s)) return ARCANA_DOMAIN;
        if (c.equals("fighter") && ARCANE_ARCHER.equalsIgnoreCase(s)) return ARCANE_ARCHER;
        if (c.equals("warlock") && VESTIGE_PATRON.equalsIgnoreCase(s)) return VESTIGE_PATRON;
        return null;
    }

    /**
     * The stored keys a character of this class, subclass and level has, in {@link #KEYS} order;
     * empty for every character but the three subclasses from level {@value #CHOICE_LEVEL}. (A
     * Vestige Patron is picked at Warlock 1, but its features, and so its choices, arrive at 3.)
     */
    public static List<String> keysFor(String clazz, String subclass, int level) {
        String name = subclassWithChoices(clazz, subclass);
        if (name == null || level < CHOICE_LEVEL) return List.of();
        return switch (name) {
            case ARCANA_DOMAIN -> List.of(SKILLS);
            case ARCANE_ARCHER -> List.of(SKILLS, ARCANE_SHOTS);
            default -> List.of(VESTIGE_TYPE, VESTIGE_DOMAIN);
        };
    }

    /** A rule of this class was broken; the message says which, in words for the player. */
    public static final class InvalidChoice extends RuntimeException {
        public InvalidChoice(String message) {
            super(message);
        }
    }

    // ── The stored object ────────────────────────────────────────────────────────

    /**
     * A set of choices: what is stored on a character, or the part of it a request sends. A null
     * component is a key that is absent. An empty {@code skills} list is a value of its own: the
     * choice was settled and no skill was owed.
     */
    public record Choices(List<String> skills, List<String> arcaneShots, String vestigeType,
                          String vestigeDomain) {

        /** No key at all. */
        public static final Choices NONE = new Choices(null, null, null, null);

        public boolean isEmpty() {
            return keys().isEmpty();
        }

        /** The keys present, in {@link #KEYS} order. */
        public List<String> keys() {
            List<String> keys = new ArrayList<>();
            if (skills != null) keys.add(SKILLS);
            if (arcaneShots != null) keys.add(ARCANE_SHOTS);
            if (vestigeType != null) keys.add(VESTIGE_TYPE);
            if (vestigeDomain != null) keys.add(VESTIGE_DOMAIN);
            return keys;
        }

        /** The object as it is stored and sent: only the keys present, in {@link #KEYS} order. */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            if (skills != null) map.put(SKILLS, skills);
            if (arcaneShots != null) map.put(ARCANE_SHOTS, arcaneShots);
            if (vestigeType != null) map.put(VESTIGE_TYPE, vestigeType);
            if (vestigeDomain != null) map.put(VESTIGE_DOMAIN, vestigeDomain);
            return map;
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Read a stored value. Forgiving, because it is only ever read back: null, blank or broken
     * JSON is no choices, and a key of the wrong shape is skipped.
     */
    public static Choices parse(String json) {
        if (json == null || json.isBlank()) return Choices.NONE;
        Map<String, Object> map;
        try {
            map = JSON.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Choices.NONE;
        }
        if (map == null) return Choices.NONE;
        return new Choices(names(map.get(SKILLS)), names(map.get(ARCANE_SHOTS)),
                name(map.get(VESTIGE_TYPE)), name(map.get(VESTIGE_DOMAIN)));
    }

    /** A JSON array of text as a list of trimmed names, or null for anything else. */
    private static List<String> names(Object value) {
        if (!(value instanceof List<?> list)) return null;
        List<String> names = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String text) || text.isBlank()) return null;
            names.add(text.trim());
        }
        return names;
    }

    /** JSON text as a trimmed name, or null for anything else. */
    private static String name(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    /**
     * Read the choices a request sends. Strict, because the player is waiting for an answer: a key
     * that is not one of {@link #KEYS}, a list key that is not a list of names and a single-value
     * key that is not a name are refused. A key sent as null is a key not sent. Values are not
     * checked here; see {@link #atLevelUp} and {@link #direct}.
     */
    public static Choices fromRequest(Map<String, Object> sent) {
        if (sent == null) return Choices.NONE;
        for (Map.Entry<String, Object> entry : sent.entrySet()) {
            String key = entry.getKey();
            if (!KEYS.contains(key)) {
                throw new InvalidChoice("'" + key + "' is not a subclass choice");
            }
            Object value = entry.getValue();
            if (value == null) continue;
            boolean list = SKILLS.equals(key) || ARCANE_SHOTS.equals(key);
            if (list && names(value) == null) {
                throw new InvalidChoice("'" + key + "' must be a list of names");
            }
            if (!list && name(value) == null) {
                throw new InvalidChoice("'" + key + "' must be a single name");
            }
        }
        return new Choices(names(sent.get(SKILLS)), names(sent.get(ARCANE_SHOTS)),
                name(sent.get(VESTIGE_TYPE)), name(sent.get(VESTIGE_DOMAIN)));
    }

    /** The value to store: the JSON object, or null when no key is present. */
    public static String write(Choices choices) {
        if (choices == null || choices.isEmpty()) return null;
        try {
            return JSON.writeValueAsString(choices.toMap());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Subclass choices could not be written", e);
        }
    }

    /**
     * A stored value fitted to the character it now belongs to: a key the class, subclass or
     * level does not have is dropped, a name that is not an option is dropped, and a list is cut
     * to the most the level allows. Returns the JSON to store, or null when nothing is left.
     */
    public static String clean(String json, String clazz, String subclass, int level) {
        return write(cleaned(parse(json), clazz, subclass, level));
    }

    /** {@link #clean} on choices already read. */
    public static Choices cleaned(Choices choices, String clazz, String subclass, int level) {
        List<String> keys = keysFor(clazz, subclass, level);
        if (keys.isEmpty() || choices == null) return Choices.NONE;
        String name = subclassWithChoices(clazz, subclass);

        List<String> skills = null;
        if (keys.contains(SKILLS) && choices.skills() != null) {
            int most = ARCANA_DOMAIN.equals(name) ? 1 : ARCANE_ARCHER_SKILLS.size();
            skills = known(choices.skills(), skillOptions(name), most);
        }
        List<String> shots = null;
        if (keys.contains(ARCANE_SHOTS) && choices.arcaneShots() != null) {
            shots = known(choices.arcaneShots(), ARCANE_SHOT_OPTIONS, arcaneShotCount(level));
            if (shots.isEmpty()) shots = null; // no option known is the same as never chosen
        }
        String type = keys.contains(VESTIGE_TYPE) ? spelling(VESTIGE_TYPES, choices.vestigeType()) : null;
        String domain = keys.contains(VESTIGE_DOMAIN) ? spelling(VESTIGE_DOMAINS, choices.vestigeDomain()) : null;
        return new Choices(skills, shots, type, domain);
    }

    /** The names that are options, in the options' spelling, each once, at most {@code most}. */
    private static List<String> known(List<String> names, List<String> options, int most) {
        Set<String> kept = new LinkedHashSet<>();
        for (String name : names) {
            String option = spelling(options, name);
            if (option != null && kept.size() < most) kept.add(option);
        }
        return List.copyOf(kept);
    }

    /** The option's own spelling of a name (case and surrounding spaces ignored), or null. */
    private static String spelling(List<String> options, String name) {
        if (name == null) return null;
        String wanted = name.trim();
        return options.stream().filter(o -> o.equalsIgnoreCase(wanted)).findFirst().orElse(null);
    }

    private static List<String> skillOptions(String subclassName) {
        return ARCANA_DOMAIN.equals(subclassName) ? ARCANA_DOMAIN_SKILLS : FIGHTER_SKILLS;
    }

    /**
     * True when the stored value of a key is all the character's level asks for: a skill choice
     * that was settled, as many Arcane Shot options as the level gives, a type or a domain that is
     * set. A key the character does not have is never complete.
     */
    public static boolean isComplete(String key, Choices stored, String clazz, String subclass, int level) {
        if (!keysFor(clazz, subclass, level).contains(key)) return false;
        Choices kept = cleaned(stored, clazz, subclass, level);
        return switch (key) {
            case SKILLS -> kept.skills() != null;
            case ARCANE_SHOTS -> kept.arcaneShots() != null && kept.arcaneShots().size() >= arcaneShotCount(level);
            case VESTIGE_TYPE -> kept.vestigeType() != null;
            case VESTIGE_DOMAIN -> kept.vestigeDomain() != null;
            default -> false;
        };
    }

    // ── Making a choice ──────────────────────────────────────────────────────────

    /**
     * The outcome of a request.
     *
     * @param choices      everything now chosen: the value to store
     * @param skillsGained skills the character becomes proficient in (the picks, and for an
     *                     Arcane Archer also Arcana and Nature)
     * @param skillsLost   picks that were replaced, whose proficiency goes with them
     */
    public record Change(Choices choices, List<String> skillsGained, List<String> skillsLost) {}

    /**
     * Refuse a key the character does not have at this level: any key on a character without one
     * of the three subclasses, another subclass's key, any key below level {@value #CHOICE_LEVEL}.
     */
    public static void assertApplicable(Choices sent, String clazz, String subclass, int level) {
        List<String> keys = keysFor(clazz, subclass, level);
        for (String key : sent.keys()) {
            if (!keys.contains(key)) {
                throw new InvalidChoice("'" + key + "' is not a choice this character makes at level " + level);
            }
        }
    }

    /**
     * The choices of a level-up, checked against the character as it will be at {@code newLevel}.
     * Keys sent replace the stored ones; keys not sent keep them.
     * <ul>
     *   <li>{@code skills}: chosen once, when the character first has the key, and never changed
     *       by a later level-up. An Arcana Domain cleric picks exactly one of
     *       {@link #ARCANA_DOMAIN_SKILLS}. An Arcane Archer gains Arcana and Nature and picks one
     *       of {@link #FIGHTER_SKILLS} for each of the two it already had; with none owed the key
     *       may be left out and is stored as an empty list. A pick must not be a skill the
     *       character is already proficient in.</li>
     *   <li>{@code arcaneShots}: exactly {@link #arcaneShotCount} options. At a level that adds an
     *       option one known option may be swapped out; at any other level every known option
     *       stays. A stored list that is short may always be filled up.</li>
     *   <li>{@code vestigeType}: one of {@link #VESTIGE_TYPES}; may change at any level-up.</li>
     *   <li>{@code vestigeDomain}: one of {@link #VESTIGE_DOMAINS}; fixed once chosen.</li>
     * </ul>
     * A key in {@link #REQUIRED} that the level leaves incomplete is refused.
     *
     * @param stored     what the character has stored (cleaned here against the new level)
     * @param sent       the request's keys ({@link Choices#NONE} when it sent none)
     * @param proficient the skills the character is proficient in before this level-up
     * @throws InvalidChoice with the reason, when a rule is broken
     */
    public static Change atLevelUp(String clazz, String subclass, int newLevel, Choices stored, Choices sent,
                                   Collection<String> proficient) {
        return change(clazz, subclass, newLevel, stored, sent, proficient, true, REQUIRED);
    }

    /** {@link #atLevelUp} with the required set given (for the tests of a key turned required). */
    static Change atLevelUp(String clazz, String subclass, int newLevel, Choices stored, Choices sent,
                            Collection<String> proficient, Set<String> required) {
        return change(clazz, subclass, newLevel, stored, sent, proficient, true, required);
    }

    /**
     * The choices of a direct edit (the subclass-choices endpoint), checked against the
     * character's current level. As {@link #atLevelUp}, without a level-up's limits: any number
     * of Arcane Shot options may be swapped, a stored domain may be changed, and stored skill
     * picks may be replaced by as many others (the replaced picks are released first, and
     * reported as {@link Change#skillsLost}). Keys not sent are not asked for.
     */
    public static Change direct(String clazz, String subclass, int level, Choices stored, Choices sent,
                                Collection<String> proficient) {
        return change(clazz, subclass, level, stored, sent, proficient, false, Set.of());
    }

    private static Change change(String clazz, String subclass, int level, Choices stored, Choices sent,
                                 Collection<String> proficient, boolean levelUp, Set<String> required) {
        Choices request = sent == null ? Choices.NONE : sent;
        assertApplicable(request, clazz, subclass, level);
        List<String> keys = keysFor(clazz, subclass, level);
        String name = subclassWithChoices(clazz, subclass);
        Choices kept = cleaned(stored, clazz, subclass, level);

        final List<String> storedSkills = kept.skills();
        List<String> skills = storedSkills;
        List<String> gained = new ArrayList<>();
        List<String> lost = new ArrayList<>();
        if (keys.contains(SKILLS)) {
            Set<String> has = lowerCased(proficient);
            if (storedSkills != null && levelUp) {
                if (request.skills() != null && !sameNames(request.skills(), storedSkills)) {
                    throw new InvalidChoice("The " + name + " skill choice has already been made");
                }
            } else if (storedSkills != null && request.skills() != null) {
                // A direct edit replaces the stored picks: they are released, then as many are picked.
                storedSkills.forEach(pick -> has.remove(pick.toLowerCase(Locale.ROOT)));
                int owed = ARCANE_ARCHER.equals(name) ? storedSkills.size() : skillsOwed(name, has);
                List<String> picks = skillPicks(name, request.skills(), owed, has);
                storedSkills.stream().filter(pick -> !picks.contains(pick)).forEach(lost::add);
                gained.addAll(picks);
                skills = picks;
            } else if (storedSkills == null && (levelUp || request.skills() != null)) {
                // The first time the choice is made. A direct edit that does not send the key
                // leaves it for later; a level-up settles it.
                int owed = skillsOwed(name, has);
                if (request.skills() != null) {
                    skills = skillPicks(name, request.skills(), owed, has);
                } else if (owed == 0) {
                    skills = List.of();
                } else if (required.contains(SKILLS)) {
                    throw new InvalidChoice(skillCountMessage(name, owed));
                }
                if (skills != null) {
                    if (ARCANE_ARCHER.equals(name)) gained.addAll(ARCANE_ARCHER_SKILLS);
                    gained.addAll(skills);
                }
            }
        }

        List<String> shots = kept.arcaneShots();
        if (keys.contains(ARCANE_SHOTS)) {
            int count = arcaneShotCount(level);
            List<String> known = shots == null ? List.of() : shots;
            if (request.arcaneShots() != null) {
                shots = arcaneShotPicks(request.arcaneShots(), level, known, levelUp);
            } else if (required.contains(ARCANE_SHOTS) && known.size() < count) {
                throw new InvalidChoice(arcaneShotCountMessage(level));
            }
        }

        String type = kept.vestigeType();
        if (keys.contains(VESTIGE_TYPE)) {
            if (request.vestigeType() != null) {
                type = spelling(VESTIGE_TYPES, request.vestigeType());
                if (type == null) {
                    throw new InvalidChoice("'" + request.vestigeType()
                            + "' is not a Vestige type (Celestial, Fiend or Undead)");
                }
            } else if (required.contains(VESTIGE_TYPE) && type == null) {
                throw new InvalidChoice("A Vestige Patron must choose a Vestige type");
            }
        }

        String domain = kept.vestigeDomain();
        if (keys.contains(VESTIGE_DOMAIN)) {
            if (request.vestigeDomain() != null) {
                String chosen = spelling(VESTIGE_DOMAINS, request.vestigeDomain());
                if (chosen == null) {
                    throw new InvalidChoice("'" + request.vestigeDomain()
                            + "' is not a Vestige domain (Life, Light, Trickery or War)");
                }
                if (levelUp && domain != null && !domain.equals(chosen)) {
                    throw new InvalidChoice("The Vestige domain cannot be changed once it is chosen");
                }
                domain = chosen;
            } else if (required.contains(VESTIGE_DOMAIN) && domain == null) {
                throw new InvalidChoice("A Vestige Patron must choose a Vestige domain");
            }
        }

        return new Change(new Choices(skills, shots, type, domain), List.copyOf(gained), List.copyOf(lost));
    }

    private static Set<String> lowerCased(Collection<String> names) {
        Set<String> out = new LinkedHashSet<>();
        if (names == null) return out;
        for (String name : names) {
            if (name != null) out.add(name.trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static boolean sameNames(List<String> a, List<String> b) {
        return lowerCased(a).equals(lowerCased(b)) && a.size() == b.size();
    }

    /**
     * How many skills the subclass lets the character pick, given the skills it is proficient in:
     * one for an Arcana Domain cleric; for an Arcane Archer one for each of Arcana and Nature it
     * already has. Never more than there are options it is not proficient in yet.
     */
    public static int skillsOwed(String clazz, String subclass, Collection<String> proficient) {
        String name = subclassWithChoices(clazz, subclass);
        if (name == null || VESTIGE_PATRON.equals(name)) return 0;
        return skillsOwed(name, lowerCased(proficient));
    }

    private static int skillsOwed(String subclassName, Set<String> has) {
        int owed = ARCANA_DOMAIN.equals(subclassName) ? 1
                : (int) ARCANE_ARCHER_SKILLS.stream().filter(s -> has.contains(s.toLowerCase(Locale.ROOT))).count();
        long open = skillOptions(subclassName).stream()
                .filter(s -> !has.contains(s.toLowerCase(Locale.ROOT))).count();
        return (int) Math.min(owed, open);
    }

    private static String skillCountMessage(String subclassName, int owed) {
        return subclassName + " requires exactly " + owed + " skill " + (owed == 1 ? "choice" : "choices");
    }

    /** The skill picks sent, in the options' spelling: exactly {@code owed}, all options, none held. */
    private static List<String> skillPicks(String subclassName, List<String> sent, int owed, Set<String> has) {
        List<String> picks = new ArrayList<>();
        for (String skill : sent) {
            String option = spelling(skillOptions(subclassName), skill);
            if (option == null) {
                throw new InvalidChoice("'" + skill + "' is not a skill " + subclassName + " can choose");
            }
            if (picks.contains(option)) {
                throw new InvalidChoice("'" + option + "' is chosen twice");
            }
            if (has.contains(option.toLowerCase(Locale.ROOT))) {
                throw new InvalidChoice("'" + option + "' is already a skill proficiency");
            }
            picks.add(option);
        }
        if (picks.size() != owed) {
            throw new InvalidChoice(skillCountMessage(subclassName, owed));
        }
        return List.copyOf(picks);
    }

    private static String arcaneShotCountMessage(int level) {
        return "An Arcane Archer knows exactly " + arcaneShotCount(level) + " Arcane Shot options at level " + level;
    }

    /**
     * The Arcane Shot options sent, in the options' spelling: all real options, none twice,
     * exactly the count for the level. With {@code limitSwaps} (a level-up) a known option may be
     * missing only at a level that adds an option, and then only one.
     */
    private static List<String> arcaneShotPicks(List<String> sent, int level, List<String> known,
                                                boolean limitSwaps) {
        List<String> picks = new ArrayList<>();
        for (String shot : sent) {
            String option = spelling(ARCANE_SHOT_OPTIONS, shot);
            if (option == null) {
                throw new InvalidChoice("'" + shot + "' is not an Arcane Shot option");
            }
            if (picks.contains(option)) {
                throw new InvalidChoice("'" + option + "' is chosen twice");
            }
            picks.add(option);
        }
        if (picks.size() != arcaneShotCount(level)) {
            throw new InvalidChoice(arcaneShotCountMessage(level));
        }
        if (limitSwaps) {
            long replaced = known.stream().filter(option -> !picks.contains(option)).count();
            boolean newOption = arcaneShotCount(level) > arcaneShotCount(level - 1);
            if (newOption && replaced > 1) {
                throw new InvalidChoice("Only one known Arcane Shot option can be replaced at level " + level);
            }
            if (!newOption && replaced > 0) {
                throw new InvalidChoice(
                        "A known Arcane Shot option can only be replaced at a level that adds a new one");
            }
        }
        return List.copyOf(picks);
    }

    // ── Spells the player picks ──────────────────────────────────────────────────

    /**
     * The spells one feature lets the player pick. The picks sit outside the class's cantrip and
     * prepared-spell counts and its spell slot cap, like a feat's.
     *
     * @param feature     the feature's name: the {@code grantedBy} of its picks
     * @param spellLevels the spell level of each pick, one entry per pick (0 = a cantrip)
     * @param names       the only spells that may be picked, or empty for any spell (which class's
     *                    list a spell is on cannot be checked here: the spell lists live in the app)
     * @param replaceable whether a level-up may swap one pick for another of the same spell level
     * @param needs       the picks in words, for a message: {@code "exactly two Wizard cantrips"}
     */
    public record SpellPicks(String feature, List<Integer> spellLevels, List<String> names,
                             boolean replaceable, String needs) {

        /** How many spells the feature has the player pick. */
        public int count() {
            return spellLevels.size();
        }

        /** True when the spells' levels are exactly the feature's picks, one spell per pick. */
        public boolean satisfiedBy(List<Integer> levels) {
            return levels.stream().sorted().toList().equals(spellLevels.stream().sorted().toList());
        }

        /** True when the feature lets this spell be picked (by name; any name when it lists none). */
        public boolean allows(String spellName) {
            return names.isEmpty() || spelling(names, spellName) != null;
        }

        /** The sentence a refused set of picks is answered with. */
        public String message() {
            return feature + " requires " + needs;
        }
    }

    /**
     * The spell-pick features a character of this class, subclass and level has:
     * <ul>
     *   <li>{@value #STUDENT_OF_ARCANA} (Arcana Domain, Cleric 3+): two Wizard cantrips.</li>
     *   <li>{@value #MAGICAL_MASTERY} (Arcana Domain, Cleric 17+): four Wizard spells, one each of
     *       level 6, 7, 8 and 9.</li>
     *   <li>{@value #ARCANE_ARCHER_LORE} (Arcane Archer, Fighter 3+): Druidcraft or
     *       Prestidigitation.</li>
     * </ul>
     * Empty for everyone else.
     */
    public static List<SpellPicks> spellPicksFor(String clazz, String subclass, int level) {
        String name = subclassWithChoices(clazz, subclass);
        if (name == null || level < CHOICE_LEVEL) return List.of();
        List<SpellPicks> picks = new ArrayList<>();
        if (ARCANA_DOMAIN.equals(name)) {
            picks.add(new SpellPicks(STUDENT_OF_ARCANA, List.of(0, 0), List.of(), true,
                    "exactly two Wizard cantrips"));
            if (level >= MAGICAL_MASTERY_LEVEL) {
                picks.add(new SpellPicks(MAGICAL_MASTERY, List.of(6, 7, 8, 9), List.of(), true,
                        "four Wizard spells, one each of level 6, 7, 8 and 9"));
            }
        }
        if (ARCANE_ARCHER.equals(name)) {
            picks.add(new SpellPicks(ARCANE_ARCHER_LORE, List.of(0), ARCANE_ARCHER_LORE_CANTRIPS, false,
                    "one cantrip, Druidcraft or Prestidigitation"));
        }
        return List.copyOf(picks);
    }
}
