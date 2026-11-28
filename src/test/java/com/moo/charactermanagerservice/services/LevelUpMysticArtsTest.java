package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.HpMode;
import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.dto.SubclassSpellcasting;
import com.moo.charactermanagerservice.models.PC;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A level-up of the Warrior of the Mystic Arts (Arcana Unleashed), the Monk subclass with
 * Spellcasting of its own: the preview's numbers, the picks at Monk 3 (the subclass is chosen in
 * the same request) and later, the spell-level cap and the slot rebuild. And the other side of
 * it: an Open Hand monk, an Eldritch Knight, a Paladin and a Wizard level up exactly as before.
 */
class LevelUpMysticArtsTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MYSTIC_ARTS = "Warrior of the Mystic Arts";

    private final LevelUpService service = new LevelUpService(JSON);

    private static PC pc(String clazz, String subclass, int level) {
        PC pc = new PC();
        pc.setClazz(clazz);
        pc.setSubclass(subclass);
        pc.setLevel((short) level);
        pc.setAbilityCon((short) 14);
        pc.setAbilityWis((short) 16);
        pc.setHpMax((short) 30);
        pc.setHpCurrent((short) 30);
        return pc;
    }

    private static Map<String, Object> spell(int level, String name) {
        Map<String, Object> spell = new LinkedHashMap<>();
        spell.put("lvl", level);
        spell.put("name", name);
        spell.put("prepared", true);
        return spell;
    }

    @SafeVarargs
    private static List<Map<String, Object>> spells(Map<String, Object>... spells) {
        return new ArrayList<>(List.of(spells));
    }

    private static List<Map<String, Object>> spellsOf(PC pc) {
        try {
            return pc.getSpells() == null ? List.of() : JSON.readValue(pc.getSpells(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> spellNames(PC pc) {
        return spellsOf(pc).stream().map(s -> String.valueOf(s.get("name"))).toList();
    }

    private PC levelUp(PC pc, String subclass, List<Map<String, Object>> newSpells) {
        return service.applyLevelUp(pc, subclass, null, null, newSpells, HpMode.AVERAGE);
    }

    /** A level-up at an Ability Score Improvement level: +2 Wisdom, plus the spells. */
    private PC levelUpWithAsi(PC pc, List<Map<String, Object>> newSpells) {
        return service.applyLevelUp(pc, null, Map.of("WIS", 2), null, newSpells, HpMode.AVERAGE);
    }

    private static void assertRefused(Runnable levelUp, String message) {
        assertThatThrownBy(levelUp::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo(message);
                });
    }

    private static final List<Map<String, Object>> THE_STARTING_FIVE = List.of(
            spell(0, "Blade Ward"), spell(0, "Thunderclap"),
            spell(1, "Jump"), spell(1, "Magic Missile"), spell(1, "Shield"));

    // ── Preview ──────────────────────────────────────────────────────────────────

    @Test
    void preview_atMonk3_carriesTheSubclassesSpellcasting_underItsName() {
        LevelUpPreview preview = service.preview(pc("Monk", null, 2));

        assertThat(preview.subclassDue()).isTrue();
        assertThat(preview.subclassOptions()).contains(MYSTIC_ARTS);
        // Without a subclass a Monk has none of it: the top-level numbers stay empty.
        assertThat(preview.newSpellSlots()).isEmpty();
        assertThat(preview.newCantripsKnown()).isZero();
        assertThat(preview.newSpellsKnown()).isZero();
        // Only the one subclass changes them.
        assertThat(preview.spellcastingBySubclass()).containsOnlyKeys(MYSTIC_ARTS);
        assertThat(preview.spellcastingBySubclass().get(MYSTIC_ARTS))
                .isEqualTo(new SubclassSpellcasting(Map.of(1, 2), 2, 3));
    }

    @Test
    void preview_spellcastingBySubclass_serializesAsTheContractSays() throws Exception {
        LevelUpPreview preview = service.preview(pc("Monk", null, 2));

        Map<String, Object> json = JSON.readValue(JSON.writeValueAsString(preview), new TypeReference<>() {});

        assertThat(json.get("spellcastingBySubclass")).isEqualTo(Map.of(MYSTIC_ARTS,
                Map.of("newSpellSlots", Map.of("1", 2), "newCantripsKnown", 2, "newSpellsKnown", 3)));
    }

    @Test
    void preview_spellcastingBySubclass_countsTheDmsBonusSlotLines() {
        PC monk = pc("Monk", null, 2);
        monk.setStatAdjustments("{\"slot1\":[{\"value\":1,\"label\":\"Boon\"}]}");

        LevelUpPreview preview = service.preview(monk);

        // The bonus slot is the character's with or without the subclass.
        assertThat(preview.newSpellSlots()).isEqualTo(Map.of(1, 1));
        assertThat(preview.spellcastingBySubclass().get(MYSTIC_ARTS).newSpellSlots()).isEqualTo(Map.of(1, 3));
    }

    @Test
    void preview_spellcastingBySubclass_isEmpty_whenNoSubclassIsDue_orNoOptionChangesTheNumbers() {
        // No subclass choice at this level.
        assertThat(service.preview(pc("Monk", null, 1)).spellcastingBySubclass()).isEmpty();
        assertThat(service.preview(pc("Monk", MYSTIC_ARTS, 6)).spellcastingBySubclass()).isEmpty();
        // A choice is due, but no option of these classes brings spellcasting of its own:
        // Eldritch Knight and Arcane Trickster are not switched on.
        for (String clazz : new String[] {"Fighter", "Rogue", "Cleric", "Wizard", "Paladin", "Barbarian"}) {
            LevelUpPreview preview = service.preview(pc(clazz, null, 2));
            assertThat(preview.subclassDue()).as(clazz).isTrue();
            assertThat(preview.spellcastingBySubclass()).as(clazz).isEmpty();
        }
    }

    @Test
    void preview_ofAMysticArtsMonk_usesItsTable() {
        LevelUpPreview six = service.preview(pc("Monk", MYSTIC_ARTS, 6));
        assertThat(six.newSpellSlots()).isEqualTo(Map.of(1, 4, 2, 2));
        assertThat(six.currentCantripsKnown()).isEqualTo(2);
        assertThat(six.newCantripsKnown()).isEqualTo(2);
        assertThat(six.currentSpellsKnown()).isEqualTo(4);
        assertThat(six.newSpellsKnown()).isEqualTo(5);

        LevelUpPreview nine = service.preview(pc("Monk", MYSTIC_ARTS, 9));
        assertThat(nine.newSpellSlots()).isEqualTo(Map.of(1, 4, 2, 3));
        assertThat(nine.currentCantripsKnown()).isEqualTo(2);
        assertThat(nine.newCantripsKnown()).isEqualTo(3);
        assertThat(nine.currentSpellsKnown()).isEqualTo(6);
        assertThat(nine.newSpellsKnown()).isEqualTo(7);
    }

    @Test
    void preview_offersTheSpellcastingFeats_toTheMysticArtsMonkOnly() {
        assertThat(service.preview(pc("Monk", MYSTIC_ARTS, 3)).featOptions())
                .contains("Evocation Adept", "Spell Subterfuge");
        assertThat(service.preview(pc("Monk", "Warrior of the Open Hand", 3)).featOptions())
                .doesNotContain("Evocation Adept", "Spell Subterfuge");
    }

    // ── Monk 3: the subclass and its first spells in one request ─────────────────

    @Test
    void atMonk3_pickingTheSubclass_takesTwoCantripsAndThreeLevel1Spells_asOrdinaryPicks() {
        PC monk = pc("Monk", null, 2);

        levelUp(monk, MYSTIC_ARTS, new ArrayList<>(THE_STARTING_FIVE));

        assertThat(monk.getLevel()).isEqualTo((short) 3);
        assertThat(monk.getSubclass()).isEqualTo(MYSTIC_ARTS);
        assertThat(spellNames(monk))
                .containsExactly("Blade Ward", "Thunderclap", "Jump", "Magic Missile", "Shield");
        // Ordinary class picks: nothing marks them always prepared or granted by a feature.
        assertThat(spellsOf(monk)).allSatisfy(s -> {
            assertThat(s).doesNotContainKey("alwaysPrepared");
            assertThat(s).doesNotContainKey("grantedBy");
        });
        // And the slots are there at once.
        assertThat(monk.getSpellSlots()).isEqualTo("{\"1\":{\"max\":2,\"used\":0}}");
        assertThat(monk.getFeatures()).contains("\"name\":\"Spellcasting\"");
    }

    @Test
    void atMonk3_fewerPicks_orNone_areAccepted_likeEveryOtherCaster() {
        PC none = pc("Monk", null, 2);
        levelUp(none, MYSTIC_ARTS, null);
        assertThat(none.getSpells()).isNull();
        assertThat(none.getSpellSlots()).isEqualTo("{\"1\":{\"max\":2,\"used\":0}}");

        PC some = pc("Monk", null, 2);
        levelUp(some, MYSTIC_ARTS, spells(spell(0, "Blade Ward"), spell(1, "Shield")));
        assertThat(spellNames(some)).containsExactly("Blade Ward", "Shield");
    }

    @Test
    void atMonk3_tooManyCantrips_orTooManySpells_areRefused() {
        PC monk = pc("Monk", null, 2);
        List<Map<String, Object>> threeCantrips = new ArrayList<>(THE_STARTING_FIVE);
        threeCantrips.add(spell(0, "Light"));
        assertRefused(() -> levelUp(monk, MYSTIC_ARTS, threeCantrips),
                "You can learn at most 2 new cantrip(s) at level 3");
        assertThat(monk.getLevel()).isEqualTo((short) 2);
        assertThat(monk.getSpells()).isNull();

        PC other = pc("Monk", null, 2);
        List<Map<String, Object>> fourSpells = new ArrayList<>(THE_STARTING_FIVE);
        fourSpells.add(spell(1, "Sleep"));
        assertRefused(() -> levelUp(other, MYSTIC_ARTS, fourSpells),
                "You can learn at most 3 new spell(s) at level 3");
        assertThat(other.getSpells()).isNull();
    }

    @Test
    void atMonk3_aLevel2Spell_isRefused_andAtMonk7_accepted() {
        PC three = pc("Monk", null, 2);
        assertRefused(() -> levelUp(three, MYSTIC_ARTS, spells(spell(2, "Misty Step"))),
                "'Misty Step' is a level-2 spell — the highest spell level castable at level 3 is 1");

        // Monk 6 -> 7: one more prepared spell, and level 2 slots arrive.
        PC six = pc("Monk", MYSTIC_ARTS, 6);
        levelUp(six, null, spells(spell(2, "Misty Step")));
        assertThat(spellNames(six)).containsExactly("Misty Step");
        assertThat(six.getSpellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":0}}");

        // And a level 3 spell is still out of reach there.
        PC another = pc("Monk", MYSTIC_ARTS, 6);
        assertRefused(() -> levelUp(another, null, spells(spell(3, "Fireball"))),
                "'Fireball' is a level-3 spell — the highest spell level castable at level 7 is 2");
    }

    @Test
    void atMonk3_anotherSubclass_takesNoSpells() {
        // A fresh character each time: a refused level-up has already set the subclass in memory.
        assertRefused(() -> levelUp(pc("Monk", null, 2), "Warrior of the Open Hand", spells(spell(0, "Blade Ward"))),
                "You can learn at most 0 new cantrip(s) at level 3");
        assertRefused(() -> levelUp(pc("Monk", null, 2), "Warrior of the Open Hand", spells(spell(1, "Shield"))),
                "You can learn at most 0 new spell(s) at level 3");
    }

    // ── Later levels: the deltas of its own table ────────────────────────────────

    @Test
    void from9To10_theThirdCantripAndOneMoreSpell() {
        PC monk = pc("Monk", MYSTIC_ARTS, 9);

        levelUp(monk, null, spells(spell(0, "Light"), spell(2, "Mirror Image")));

        assertThat(spellNames(monk)).containsExactly("Light", "Mirror Image");
        assertThat(monk.getSpellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":3,\"used\":0}}");

        PC greedy = pc("Monk", MYSTIC_ARTS, 9);
        assertRefused(() -> levelUp(greedy, null, spells(spell(0, "Light"), spell(0, "Mending"))),
                "You can learn at most 1 new cantrip(s) at level 10");
    }

    @Test
    void aLevelWithNoNewCantrip_refusesOne_andALevelWithNoNewSpell_refusesOne() {
        // 4 -> 5: the Prepared Spells column stays at 4 and the cantrips at 2.
        PC monk = pc("Monk", MYSTIC_ARTS, 4);
        assertRefused(() -> levelUp(monk, null, spells(spell(0, "Light"))),
                "You can learn at most 0 new cantrip(s) at level 5");
        assertRefused(() -> levelUp(monk, null, spells(spell(1, "Sleep"))),
                "You can learn at most 0 new spell(s) at level 5");
    }

    @Test
    void theSlotsAreRebuilt_fromItsTable_keepingWhatIsSpent() {
        PC monk = pc("Monk", MYSTIC_ARTS, 6);
        monk.setSpellSlots("{\"1\":{\"max\":3,\"used\":2}}");

        levelUp(monk, null, null);

        assertThat(monk.getSpellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":2},\"2\":{\"max\":2,\"used\":0}}");
    }

    @Test
    void theSlotsFollowTheTable_atEveryLevelUpFrom3To20() {
        PC monk = pc("Monk", MYSTIC_ARTS, 3);
        monk.setAbilityWis((short) 10); // room for the five Ability Score Improvements on the way
        String[] expected = {
                "{\"1\":{\"max\":3,\"used\":0}}", "{\"1\":{\"max\":3,\"used\":0}}", "{\"1\":{\"max\":3,\"used\":0}}",
                "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":0}}",
                "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":0}}",
                "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":0}}",
                "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":3,\"used\":0}}",
                "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":3,\"used\":0}}",
                "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":3,\"used\":0}}",
                "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":3,\"used\":0},\"3\":{\"max\":2,\"used\":0}}",
                "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":3,\"used\":0},\"3\":{\"max\":2,\"used\":0}}",
                "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":3,\"used\":0},\"3\":{\"max\":2,\"used\":0}}",
                "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":3,\"used\":0},\"3\":{\"max\":3,\"used\":0}}",
                "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":3,\"used\":0},\"3\":{\"max\":3,\"used\":0}}",
                "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":3,\"used\":0},\"3\":{\"max\":3,\"used\":0}}",
                "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":3,\"used\":0},\"3\":{\"max\":3,\"used\":0},\"4\":{\"max\":1,\"used\":0}}",
                "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":3,\"used\":0},\"3\":{\"max\":3,\"used\":0},\"4\":{\"max\":1,\"used\":0}}",
        };
        for (int newLevel = 4; newLevel <= 20; newLevel++) {
            if (com.moo.charactermanagerservice.progression.ClassProgression.isAsiLevel("Monk", newLevel)) {
                levelUpWithAsi(monk, null);
            } else {
                levelUp(monk, null, null);
            }
            assertThat(monk.getSpellSlots()).as("Monk " + newLevel).isEqualTo(expected[newLevel - 4]);
        }
    }

    @Test
    void theDmsBonusSlotLines_stayOnTopOfItsTable() {
        PC monk = pc("Monk", MYSTIC_ARTS, 6);
        monk.setStatAdjustments("{\"slot3\":[{\"value\":1,\"label\":\"Pearl of Power\"}]}");

        levelUp(monk, null, null);

        assertThat(monk.getSpellSlots()).isEqualTo(
                "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":0},\"3\":{\"max\":1,\"used\":0}}");
    }

    @Test
    void aSchoolAdeptFeat_isAccepted_andItsSpellsArriveWithTheMonksSlotLevels() {
        PC monk = pc("Monk", MYSTIC_ARTS, 3);

        service.applyLevelUp(monk, null, null, "Evocation Adept");

        assertThat(monk.getLevel()).isEqualTo((short) 4);
        assertThat(CharacterFeats.namesOn(monk)).contains("Evocation Adept");
        // One spell per spell slot level it has: one now, a second owed when level 2 slots arrive.
        assertThat(service.preview(monk).alwaysPreparedSpells()).hasSize(1);
        monk.setLevel((short) 6);
        assertThat(service.preview(monk).alwaysPreparedSpells()).hasSize(2);
    }

    // ── Existing characters are unaffected ───────────────────────────────────────

    @Test
    void anOpenHandMonk_levelsUpExactlyAsBefore() {
        PC monk = pc("Monk", "Warrior of the Open Hand", 6);
        LevelUpPreview preview = service.preview(monk);
        assertThat(preview.currentSpellSlots()).isEmpty();
        assertThat(preview.newSpellSlots()).isEmpty();
        assertThat(preview.newCantripsKnown()).isZero();
        assertThat(preview.newSpellsKnown()).isZero();
        assertThat(preview.spellcastingBySubclass()).isEmpty();

        assertRefused(() -> levelUp(monk, null, spells(spell(1, "Shield"))),
                "You can learn at most 0 new spell(s) at level 7");
        levelUp(monk, null, null);
        assertThat(monk.getLevel()).isEqualTo((short) 7);
        assertThat(monk.getSpellSlots()).isNull();
        assertThat(monk.getSpells()).isNull();
    }

    @Test
    void anEldritchKnight_stillGetsNoSlotsAndNoPicks() {
        PC choosing = pc("Fighter", null, 2);
        LevelUpPreview atThree = service.preview(choosing);
        assertThat(atThree.subclassOptions()).contains("Eldritch Knight");
        assertThat(atThree.spellcastingBySubclass()).isEmpty();
        assertRefused(() -> levelUp(pc("Fighter", null, 2), "Eldritch Knight", spells(spell(0, "Fire Bolt"))),
                "You can learn at most 0 new cantrip(s) at level 3");
        assertRefused(() -> levelUp(pc("Fighter", null, 2), "Eldritch Knight", spells(spell(1, "Shield"))),
                "You can learn at most 0 new spell(s) at level 3");
        levelUp(choosing, "Eldritch Knight", null);
        assertThat(choosing.getSpellSlots()).isNull();

        PC knight = pc("Fighter", "Eldritch Knight", 6);
        LevelUpPreview preview = service.preview(knight);
        assertThat(preview.newSpellSlots()).isEmpty();
        assertThat(preview.newCantripsKnown()).isZero();
        assertThat(preview.newSpellsKnown()).isZero();
        assertThat(preview.featOptions()).doesNotContain("Evocation Adept");
        levelUp(knight, null, null);
        assertThat(knight.getSpellSlots()).isNull();
    }

    @Test
    void aPaladin_levelsUpExactlyAsBefore() {
        PC paladin = pc("Paladin", "Oath of Devotion", 4);
        LevelUpPreview preview = service.preview(paladin);
        assertThat(preview.newSpellSlots()).isEqualTo(Map.of(1, 4, 2, 2));
        assertThat(preview.currentCantripsKnown()).isZero();
        assertThat(preview.newCantripsKnown()).isZero();
        assertThat(preview.currentSpellsKnown()).isEqualTo(5);
        assertThat(preview.newSpellsKnown()).isEqualTo(6);
        assertThat(preview.spellcastingBySubclass()).isEmpty();

        levelUp(paladin, null, spells(spell(2, "Aid")));
        assertThat(spellNames(paladin)).containsExactly("Aid");
        assertThat(paladin.getSpellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":0}}");

        // Paladin 2 -> 3 picks an oath: its spell count still moves by the class column alone.
        PC choosing = pc("Paladin", null, 2);
        assertThat(service.preview(choosing).spellcastingBySubclass()).isEmpty();
        assertRefused(() -> levelUp(choosing, "Oath of Devotion", spells(spell(1, "Bless"), spell(1, "Heroism"))),
                "You can learn at most 1 new spell(s) at level 3");
    }

    @Test
    void aWizard_levelsUpExactlyAsBefore() {
        PC wizard = pc("Wizard", "Evoker", 4);
        LevelUpPreview preview = service.preview(wizard);
        assertThat(preview.newSpellSlots()).isEqualTo(Map.of(1, 4, 2, 3, 3, 2));
        assertThat(preview.currentCantripsKnown()).isEqualTo(4);
        assertThat(preview.newCantripsKnown()).isEqualTo(4);
        assertThat(preview.currentSpellsKnown()).isEqualTo(7);
        assertThat(preview.newSpellsKnown()).isEqualTo(9);
        assertThat(preview.spellcastingBySubclass()).isEmpty();

        levelUp(wizard, null, spells(spell(3, "Fireball"), spell(3, "Fly")));
        assertThat(spellNames(wizard)).containsExactly("Fireball", "Fly");
        assertThat(wizard.getSpellSlots()).isEqualTo(
                "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":3,\"used\":0},\"3\":{\"max\":2,\"used\":0}}");

        PC greedy = pc("Wizard", "Evoker", 4);
        assertRefused(() -> levelUp(greedy, null,
                        spells(spell(3, "Fireball"), spell(3, "Fly"), spell(3, "Haste"))),
                "You can learn at most 2 new spell(s) at level 5");
        assertRefused(() -> levelUp(greedy, null, spells(spell(4, "Banishment"))),
                "'Banishment' is a level-4 spell — the highest spell level castable at level 5 is 3");
    }
}
