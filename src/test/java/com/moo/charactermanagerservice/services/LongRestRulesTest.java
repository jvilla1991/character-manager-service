package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.LongRestChoices;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.services.LongRestRules.SpellChange;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The long rest choice rules: prepared spells and the Death Knight's rune weapon. */
class LongRestRulesTest {

    // Bless and Aid prepared, Command explicitly not, Shield with no flag (older
    // data: counts as prepared), plus a cantrip.
    private static final String SPELLS = "["
            + "{\"name\":\"Guidance\",\"lvl\":0,\"prepared\":true},"
            + "{\"name\":\"Bless\",\"lvl\":1,\"prepared\":true},"
            + "{\"name\":\"Command\",\"lvl\":1,\"prepared\":false},"
            + "{\"name\":\"Shield\",\"lvl\":1},"
            + "{\"name\":\"Aid\",\"lvl\":2,\"prepared\":true},"
            + "{\"name\":\"Hold Person\",\"lvl\":2,\"prepared\":false}]";

    private static PC caster(String clazz, int level) {
        PC pc = new PC();
        pc.setId(7L);
        pc.setName("Mira");
        pc.setClazz(clazz);
        pc.setLevel((short) level);
        pc.setSpells(SPELLS);
        return pc;
    }

    private static PC deathKnight(int level, String rune) {
        PC pc = new PC();
        pc.setId(9L);
        pc.setName("Thorne");
        pc.setClazz("Paladin");
        pc.setSubclass("Oath of the Ebon Blade");
        pc.setSpecialization(rune);
        pc.setLevel((short) level);
        pc.setAbilityCha((short) 16); // +3
        pc.setInventory("["
                + "{\"name\":\"Greatsword\",\"category\":\"weapon\",\"qty\":1,\"properties\":[\"heavy\",\"two-handed\"]},"
                + "{\"name\":\"Longbow\",\"category\":\"weapon\",\"qty\":1,\"properties\":[\"ammunition (150/600)\",\"heavy\"]},"
                + "{\"name\":\"Handaxe\",\"category\":\"weapon\",\"qty\":2,\"properties\":[\"light\",\"thrown (20/60)\"]},"
                + "{\"name\":\"Mace\",\"category\":\"weapon\",\"qty\":1,\"status\":\"dropped\"},"
                + "{\"name\":\"Chain Mail\",\"category\":\"armor\",\"qty\":1}]");
        pc.setWeapons("[{\"name\":\"Runeblade\",\"dmg\":\"1d8\"},{\"name\":\"Light Crossbow\",\"dmg\":\"1d8\"}]");
        return pc;
    }

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    private static void assertRejected(Runnable call, String messagePart) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400))
                .hasMessageContaining(messagePart);
    }

    // --- who may change what ---

    @Test
    void spellChange_followsTheClass() {
        assertThat(LongRestRules.spellChangeFor("Cleric")).isEqualTo(SpellChange.ANY);
        assertThat(LongRestRules.spellChangeFor("druid")).isEqualTo(SpellChange.ANY);
        assertThat(LongRestRules.spellChangeFor(" Wizard ")).isEqualTo(SpellChange.ANY);
        assertThat(LongRestRules.spellChangeFor("Paladin")).isEqualTo(SpellChange.ONE);
        assertThat(LongRestRules.spellChangeFor("Ranger")).isEqualTo(SpellChange.ONE);
        assertThat(LongRestRules.spellChangeFor("Bard")).isEqualTo(SpellChange.NONE);
        assertThat(LongRestRules.spellChangeFor("Sorcerer")).isEqualTo(SpellChange.NONE);
        assertThat(LongRestRules.spellChangeFor("Warlock")).isEqualTo(SpellChange.NONE);
        assertThat(LongRestRules.spellChangeFor("Fighter")).isEqualTo(SpellChange.NONE);
        assertThat(LongRestRules.spellChangeFor(null)).isEqualTo(SpellChange.NONE);
    }

    @Test
    void preparedSpellCap_matchesTheClientTable() {
        assertThat(LongRestRules.preparedSpellCap("Cleric", (short) 1)).isEqualTo(4);
        assertThat(LongRestRules.preparedSpellCap("Cleric", (short) 5)).isEqualTo(9);
        assertThat(LongRestRules.preparedSpellCap("Paladin", (short) 1)).isEqualTo(2);
        assertThat(LongRestRules.preparedSpellCap("Paladin", (short) 5)).isEqualTo(6);
        assertThat(LongRestRules.preparedSpellCap("Ranger", (short) 20)).isEqualTo(15);
        assertThat(LongRestRules.preparedSpellCap("Wizard", (short) 20)).isEqualTo(25);
        assertThat(LongRestRules.preparedSpellCap("Wizard", (short) 40)).isEqualTo(25); // clamped
        assertThat(LongRestRules.preparedSpellCap("Wizard", null)).isEqualTo(4);
        assertThat(LongRestRules.preparedSpellCap("Fighter", (short) 5)).isNull();      // no table, no cap
    }

    @Test
    void preparedNow_countsLeveledSpells_andTreatsAMissingFlagAsPrepared() {
        assertThat(LongRestRules.preparedNow(caster("Cleric", 3)))
                .containsExactly("bless", "shield", "aid"); // no cantrip, no Command, no Hold Person
    }

    // --- prepared list validation ---

    @Test
    void cleric_mayChangeAnyNumber() {
        PC cleric = caster("Cleric", 3); // cap 6
        assertThatCode(() -> LongRestRules.validatePrepared(cleric,
                List.of("Command", "Hold Person", "bless"))).doesNotThrowAnyException();
    }

    @Test
    void aNullList_isNoChange_forAnyone() {
        assertThatCode(() -> LongRestRules.validatePrepared(caster("Bard", 3), null)).doesNotThrowAnyException();
    }

    @Test
    void rejects_aSpellThatIsNotOnTheSheet() {
        assertRejected(() -> LongRestRules.validatePrepared(caster("Cleric", 3), List.of("Bless", "Fireball")),
                "Fireball is not on Mira's spell list");
    }

    @Test
    void rejects_aCantrip() {
        assertRejected(() -> LongRestRules.validatePrepared(caster("Cleric", 3), List.of("Bless", "Guidance")),
                "Guidance is a cantrip");
    }

    @Test
    void rejects_goingOverTheCap() {
        PC cleric = caster("Cleric", 1); // cap 4, five leveled spells on the sheet
        assertRejected(() -> LongRestRules.validatePrepared(cleric,
                        List.of("Bless", "Command", "Shield", "Aid", "Hold Person")),
                "That is 5 prepared spells, and the limit is 4");
    }

    @Test
    void onlyRemoving_isAllowed_evenWhenStillOverTheCap() {
        PC paladin = caster("Paladin", 1); // cap 2, three prepared from older data
        assertThatCode(() -> LongRestRules.validatePrepared(paladin, List.of("Bless", "Aid")))
                .doesNotThrowAnyException();
    }

    @Test
    void paladin_mayMakeOneSwap() {
        PC paladin = caster("Paladin", 5); // cap 6; Bless, Shield, Aid prepared
        assertThatCode(() -> LongRestRules.validatePrepared(paladin, List.of("Command", "Shield", "Aid")))
                .doesNotThrowAnyException(); // Bless out, Command in
    }

    @Test
    void paladin_mayJustAddOne_whenUnderTheCap() {
        PC paladin = caster("Paladin", 5);
        assertThatCode(() -> LongRestRules.validatePrepared(paladin, List.of("Bless", "Shield", "Aid", "Command")))
                .doesNotThrowAnyException();
    }

    @Test
    void ranger_cannotSwapTwo() {
        PC ranger = caster("Ranger", 5);
        assertRejected(() -> LongRestRules.validatePrepared(ranger, List.of("Command", "Hold Person", "Aid")),
                "Ranger heroes can replace only one prepared spell at a long rest");
    }

    @Test
    void paladin_cannotAddTwo() {
        PC paladin = caster("Paladin", 5);
        assertRejected(() -> LongRestRules.validatePrepared(paladin,
                        List.of("Bless", "Shield", "Aid", "Command", "Hold Person")),
                "can replace only one prepared spell");
    }

    @Test
    void bard_cannotChange_butMaySendTheCurrentList() {
        PC bard = caster("Bard", 3);
        assertThatCode(() -> LongRestRules.validatePrepared(bard, List.of("Aid", "Shield", "Bless")))
                .doesNotThrowAnyException(); // the same set, in any order: no change
        assertRejected(() -> LongRestRules.validatePrepared(bard, List.of("Command", "Shield", "Aid")),
                "Bard heroes do not change prepared spells at a long rest");
    }

    @Test
    void applyPrepared_writesAnExplicitFlagOnEveryLeveledSpell_andSaysWhatChanged() {
        PC cleric = caster("Cleric", 3);

        String change = LongRestRules.applyPrepared(cleric, List.of("command", "Shield", "Aid"));

        assertThat(change).isEqualTo("prepared Command; unprepared Bless");
        assertThat(LongRestRules.preparedNow(cleric)).containsExactly("command", "shield", "aid");
        assertThat(cleric.getSpells())
                .contains("{\"name\":\"Shield\",\"lvl\":1,\"prepared\":true}")   // flag made explicit
                .contains("{\"name\":\"Guidance\",\"lvl\":0,\"prepared\":true}"); // cantrip untouched
    }

    @Test
    void applyPrepared_changesNothing_forTheSameList() {
        PC cleric = caster("Cleric", 3);
        assertThat(LongRestRules.applyPrepared(cleric, List.of("Bless", "Shield", "Aid"))).isNull();
        assertThat(cleric.getSpells()).isEqualTo(SPELLS);
        assertThat(LongRestRules.applyPrepared(cleric, null)).isNull();
    }

    @Test
    void applyPrepared_summarizesABigChange() {
        PC cleric = caster("Cleric", 3);
        String change = LongRestRules.applyPrepared(cleric, List.of("Command", "Hold Person"));
        assertThat(change).isEqualTo("prepared spells changed (2 in, 3 out)");
    }

    // --- rune weapon ---

    @Test
    void isRuneforger_needsTheEbonBladeAtLevelThree() {
        assertThat(LongRestRules.isRuneforger(deathKnight(3, "Blood"))).isTrue();
        assertThat(LongRestRules.isRuneforger(deathKnight(2, "Blood"))).isFalse();
        PC plainPaladin = deathKnight(5, null);
        plainPaladin.setSubclass("Oath of Devotion");
        assertThat(LongRestRules.isRuneforger(plainPaladin)).isFalse();
        PC wrongClass = deathKnight(5, "Blood");
        wrongClass.setClazz("Ranger");
        assertThat(LongRestRules.isRuneforger(wrongClass)).isFalse();
    }

    @Test
    void meleeWeapons_leavesOutRangedDroppedAndNonWeapons_andAddsTheOlderWeaponList() {
        assertThat(LongRestRules.meleeWeapons(deathKnight(5, "Blood")))
                .containsExactly("Greatsword", "Handaxe", "Runeblade");
    }

    @Test
    void isRanged_readsTheAmmunitionProperty_orATellingName() {
        assertThat(LongRestRules.isRanged("Hand Cannon", List.of("Ammunition (30/90)"))).isTrue();
        assertThat(LongRestRules.isRanged("Shortbow", null)).isTrue();
        assertThat(LongRestRules.isRanged("Heavy Crossbow", null)).isTrue();
        assertThat(LongRestRules.isRanged("Sling", null)).isTrue();
        assertThat(LongRestRules.isRanged("Dart", List.of("finesse", "thrown (20/60)"))).isTrue();
        assertThat(LongRestRules.isRanged("Dagger", List.of("finesse", "light", "thrown (20/60)"))).isFalse();
        assertThat(LongRestRules.isRanged("Longsword", List.of("versatile (1d10)"))).isFalse();
    }

    @Test
    void isRanged_asksTheCatalogFirst_byKeyThenByExactName() {
        // The catalog knows these whatever their properties say (or do not say).
        assertThat(LongRestRules.isRanged("Dart", null)).isTrue();
        assertThat(LongRestRules.isRanged("net", null)).isTrue();
        assertThat(LongRestRules.isRanged("Javelin", List.of("thrown (30/120)"))).isFalse();
        assertThat(LongRestRules.isRanged("Trident", null)).isFalse();
        // A renamed catalog item is still what its key says.
        assertThat(LongRestRules.isRanged("Heartseeker", "longbow", null)).isTrue();
        assertThat(LongRestRules.isRanged("Bowbreaker", "greataxe", null)).isFalse(); // the name alone would say ranged
    }

    @Test
    void isRanged_fallsBackToPropertiesAndName_forWeaponsTheCatalogDoesNotKnow() {
        assertThat(LongRestRules.isRanged("Musket", null, null)).isTrue();
        assertThat(LongRestRules.isRanged("Pistol", null, null)).isTrue();
        assertThat(LongRestRules.isRanged("Elven Courtbow", null, null)).isTrue();
        assertThat(LongRestRules.isRanged("Thunder Tube", null, List.of("Ammunition (40/120)"))).isTrue();
        assertThat(LongRestRules.isRanged("Greataxe 'Splitter'", null, null)).isFalse();
        assertThat(LongRestRules.isRanged("Handaxes (4)", null, null)).isFalse();
        assertThat(LongRestRules.isRanged("Frostmourne", "frostmourne", null)).isFalse();
    }

    @Test
    void meleeWeapons_usesTheCatalogKeyOnInventoryItems() {
        PC knight = deathKnight(5, "Blood");
        knight.setWeapons("[]");
        knight.setInventory("["
                + "{\"name\":\"Heartseeker\",\"catalogKey\":\"longbow\",\"category\":\"weapon\",\"qty\":1},"
                + "{\"name\":\"Bowbreaker\",\"catalogKey\":\"greataxe\",\"category\":\"weapon\",\"qty\":1},"
                + "{\"name\":\"Dart\",\"category\":\"weapon\",\"qty\":5}]");

        assertThat(LongRestRules.meleeWeapons(knight)).containsExactly("Bowbreaker");
    }

    @Test
    void requireRuneChoice_onlyBindsAKnightWithAMeleeWeaponAndNoRuneOnOne() {
        PC knight = deathKnight(5, "Blood");
        assertRejected(() -> LongRestRules.requireRuneChoice(knight, null),
                "Choose the weapon that carries Thorne's rune before readying");
        assertRejected(() -> LongRestRules.requireRuneChoice(knight, new LongRestChoices(List.of(), " ")),
                "Choose the weapon");
        assertThatCode(() -> LongRestRules.requireRuneChoice(knight, new LongRestChoices(null, "Greatsword")))
                .doesNotThrowAnyException();

        PC inscribed = deathKnight(5, "Blood");
        inscribed.setRuneWeapon("Greatsword");
        assertThatCode(() -> LongRestRules.requireRuneChoice(inscribed, null)).doesNotThrowAnyException();

        PC unarmed = deathKnight(5, "Blood");
        unarmed.setInventory("[]");
        unarmed.setWeapons("[]");
        assertThatCode(() -> LongRestRules.requireRuneChoice(unarmed, null)).doesNotThrowAnyException();

        assertThatCode(() -> LongRestRules.requireRuneChoice(deathKnight(2, "Blood"), null))
                .doesNotThrowAnyException(); // no Runeforging yet
        assertThatCode(() -> LongRestRules.requireRuneChoice(caster("Cleric", 5), null))
                .doesNotThrowAnyException();
    }

    @Test
    void keepStoredPreparedFlags_restoresEveryStoredFlag_includingAMissingOne() {
        PC existing = caster("Cleric", 3); // Bless true, Command false, Shield no flag, Aid true, Hold Person false
        PC incoming = caster("Cleric", 3);
        incoming.setSpells("["
                + "{\"name\":\"Guidance\",\"lvl\":0,\"prepared\":false},"
                + "{\"name\":\"Bless\",\"lvl\":1,\"prepared\":false},"
                + "{\"name\":\"Command\",\"lvl\":1,\"prepared\":true},"
                + "{\"name\":\"Shield\",\"lvl\":1,\"prepared\":false},"
                + "{\"name\":\"Aid\",\"lvl\":2},"
                + "{\"name\":\"Hold Person\",\"lvl\":2,\"prepared\":false}]");

        LongRestRules.keepStoredPreparedFlags(incoming, existing);

        assertThat(LongRestRules.preparedNow(incoming)).containsExactly("bless", "shield", "aid");
        assertThat(incoming.getSpells())
                .contains("{\"name\":\"Shield\",\"lvl\":1}")                          // still no flag
                .contains("{\"name\":\"Aid\",\"lvl\":2,\"prepared\":true}")
                .contains("{\"name\":\"Guidance\",\"lvl\":0,\"prepared\":false}");   // cantrips are not policed
    }

    // --- always-prepared spells (alwaysPrepared on the saved spell) ---

    private static PC rangerWithHuntersMark() {
        PC ranger = caster("Ranger", 1); // cap 2
        ranger.setSpells("["
                + "{\"name\":\"Hunter's Mark\",\"lvl\":1,\"prepared\":true,\"alwaysPrepared\":true,\"grantedBy\":\"Favored Enemy\"},"
                + "{\"name\":\"Goodberry\",\"lvl\":1,\"prepared\":true},"
                + "{\"name\":\"Cure Wounds\",\"lvl\":1,\"prepared\":true},"
                + "{\"name\":\"Jump\",\"lvl\":1,\"prepared\":false}]");
        return ranger;
    }

    @Test
    void alwaysPreparedSpells_areOutsideThePreparedList_andNeverEatTheCap() {
        PC ranger = rangerWithHuntersMark();
        assertThat(LongRestRules.preparedNow(ranger)).containsExactly("goodberry", "cure wounds");

        // Two chosen spells is exactly the level 1 cap, with Hunter's Mark on top: a swap is fine.
        assertThatCode(() -> LongRestRules.validatePrepared(ranger, List.of("Goodberry", "Jump")))
                .doesNotThrowAnyException();
        // A list that names the always-prepared spell is read without it.
        assertThatCode(() -> LongRestRules.validatePrepared(ranger, List.of("Hunter's Mark", "Goodberry", "Jump")))
                .doesNotThrowAnyException();
        // A third chosen spell is still over the cap.
        assertRejected(() -> LongRestRules.validatePrepared(ranger, List.of("Goodberry", "Cure Wounds", "Jump")),
                "the limit is 2");
    }

    @Test
    void applyPrepared_leavesAlwaysPreparedSpellsAlone() {
        PC ranger = rangerWithHuntersMark();
        String change = LongRestRules.applyPrepared(ranger, List.of("Goodberry", "Jump"));

        assertThat(change).isEqualTo("prepared Jump; unprepared Cure Wounds");
        assertThat(ranger.getSpells())
                .contains("{\"name\":\"Hunter's Mark\",\"lvl\":1,\"prepared\":true,\"alwaysPrepared\":true,\"grantedBy\":\"Favored Enemy\"}")
                .contains("{\"name\":\"Cure Wounds\",\"lvl\":1,\"prepared\":false}")
                .contains("{\"name\":\"Jump\",\"lvl\":1,\"prepared\":true}");
        // Naming it, or leaving it out, changes nothing.
        assertThat(LongRestRules.applyPrepared(ranger, List.of("Hunter's Mark", "Goodberry", "Jump"))).isNull();
    }

    @Test
    void keepStoredPreparedFlags_alsoKeepsTheAlwaysPreparedMarker() {
        PC existing = rangerWithHuntersMark();
        PC incoming = caster("Ranger", 1);
        // An owner's save that drops the marker from Hunter's Mark and puts one on Goodberry.
        incoming.setSpells("["
                + "{\"name\":\"Hunter's Mark\",\"lvl\":1,\"prepared\":true},"
                + "{\"name\":\"Goodberry\",\"lvl\":1,\"prepared\":true,\"alwaysPrepared\":true,\"grantedBy\":\"Me\"},"
                + "{\"name\":\"Cure Wounds\",\"lvl\":1,\"prepared\":true},"
                + "{\"name\":\"Jump\",\"lvl\":1,\"prepared\":false},"
                + "{\"name\":\"Alarm\",\"lvl\":1,\"prepared\":true,\"alwaysPrepared\":true,\"grantedBy\":\"DM grant\"}]");

        LongRestRules.keepStoredPreparedFlags(incoming, existing);

        assertThat(LongRestRules.preparedNow(incoming)).containsExactly("goodberry", "cure wounds");
        assertThat(incoming.getSpells())
                .contains("\"name\":\"Hunter's Mark\",\"lvl\":1,\"prepared\":true,\"alwaysPrepared\":true,\"grantedBy\":\"Favored Enemy\"")
                .contains("{\"name\":\"Goodberry\",\"lvl\":1,\"prepared\":true}")
                // A spell that is new in the body keeps what it arrives with.
                .contains("\"name\":\"Alarm\",\"lvl\":1,\"prepared\":true,\"alwaysPrepared\":true,\"grantedBy\":\"DM grant\"");
    }

    @Test
    void keepStoredPreparedFlags_leavesAnUnchangedBodyByteForByte() {
        PC existing = caster("Cleric", 3);
        PC incoming = caster("Cleric", 3);
        LongRestRules.keepStoredPreparedFlags(incoming, existing);
        assertThat(incoming.getSpells()).isEqualTo(SPELLS);

        PC noSpells = caster("Cleric", 3);
        noSpells.setSpells(null);
        LongRestRules.keepStoredPreparedFlags(noSpells, existing);
        assertThat(noSpells.getSpells()).isNull();
    }

    @Test
    void runeWeaponOf_isUnset_whenTheStoredWeaponIsGone() {
        PC knight = deathKnight(5, "Blood");
        assertThat(LongRestRules.runeWeaponOf(knight)).isNull();

        knight.setRuneWeapon("greatsword");
        assertThat(LongRestRules.runeWeaponOf(knight)).isEqualTo("Greatsword"); // the sheet's spelling

        knight.setRuneWeapon("Frostmourne"); // sold since
        assertThat(LongRestRules.runeWeaponOf(knight)).isNull();

        knight.setRuneWeapon("Mace"); // dropped
        assertThat(LongRestRules.runeWeaponOf(knight)).isNull();
    }

    @Test
    void validateRuneWeapon_acceptsAMeleeWeaponTheKnightHas() {
        assertThatCode(() -> LongRestRules.validateRuneWeapon(deathKnight(3, "Frost"), "Handaxe"))
                .doesNotThrowAnyException();
        assertThatCode(() -> LongRestRules.validateRuneWeapon(deathKnight(3, "Frost"), null))
                .doesNotThrowAnyException();
    }

    @Test
    void validateRuneWeapon_rejectsARangedOrMissingWeapon_andANonDeathKnight() {
        assertRejected(() -> LongRestRules.validateRuneWeapon(deathKnight(3, "Blood"), "Longbow"),
                "Longbow is not a melee weapon Thorne has");
        assertRejected(() -> LongRestRules.validateRuneWeapon(deathKnight(3, "Blood"), "Frostmourne"),
                "Frostmourne is not a melee weapon Thorne has");
        assertRejected(() -> LongRestRules.validateRuneWeapon(deathKnight(2, "Blood"), "Greatsword"),
                "Only a Death Knight of level 3 or higher has a rune weapon");
    }

    @Test
    void applyRuneWeapon_inscribesThenMoves() {
        PC knight = deathKnight(5, "Blood");

        assertThat(LongRestRules.applyRuneWeapon(knight, "greatsword"))
                .isEqualTo("Blood Rune inscribed on Greatsword");
        assertThat(knight.getRuneWeapon()).isEqualTo("Greatsword");

        assertThat(LongRestRules.applyRuneWeapon(knight, "Greatsword")).isNull(); // already there

        assertThat(LongRestRules.applyRuneWeapon(knight, "Runeblade"))
                .isEqualTo("Blood Rune moved from Greatsword to Runeblade");
    }

    @Test
    void bloodRuneTempHp_isChaModPlusLevel_onlyForAnInscribedBloodRune() {
        PC blood = deathKnight(5, "Blood");
        assertThat(LongRestRules.bloodRuneTempHp(blood)).isZero(); // nothing inscribed yet

        blood.setRuneWeapon("Greatsword");
        assertThat(LongRestRules.bloodRuneTempHp(blood)).isEqualTo(8); // +3 CHA + level 5

        PC frost = deathKnight(5, "Frost");
        frost.setRuneWeapon("Greatsword");
        assertThat(LongRestRules.bloodRuneTempHp(frost)).isZero();

        PC young = deathKnight(2, "Blood");
        young.setRuneWeapon("Greatsword");
        assertThat(LongRestRules.bloodRuneTempHp(young)).isZero(); // no Runeforging before level 3
    }

    @Test
    void bloodRuneTempHp_neverGoesBelowZero() {
        PC knight = deathKnight(3, "Blood");
        knight.setRuneWeapon("Greatsword");
        knight.setAbilityCha((short) 3); // -4
        assertThat(LongRestRules.bloodRuneTempHp(knight)).isZero(); // max(-4 + 3, 0)
    }

    // --- whole-character saves ---

    @Test
    void normalizeRuneWeapon_keepsTheStoredOne_whenTheBodyOmitsIt() {
        PC existing = deathKnight(5, "Blood");
        existing.setRuneWeapon("Greatsword");
        PC incoming = deathKnight(5, "Blood"); // runeWeapon null
        assertThat(LongRestRules.normalizeRuneWeapon(incoming, existing)).isEqualTo("Greatsword");
    }

    @Test
    void normalizeRuneWeapon_takesTheBodysWeapon_whenItIsAMeleeWeaponOnTheSheet() {
        PC existing = deathKnight(5, "Blood");
        existing.setRuneWeapon("Greatsword");
        PC incoming = deathKnight(5, "Blood");
        incoming.setRuneWeapon("handaxe");
        assertThat(LongRestRules.normalizeRuneWeapon(incoming, existing)).isEqualTo("Handaxe");
    }

    @Test
    void normalizeRuneWeapon_unsetsWhatNoLongerFits_insteadOfFailingTheSave() {
        PC existing = deathKnight(5, "Blood");
        existing.setRuneWeapon("Greatsword");

        PC soldIt = deathKnight(5, "Blood");
        soldIt.setInventory("[]");
        assertThat(LongRestRules.normalizeRuneWeapon(soldIt, existing)).isNull();

        PC ranged = deathKnight(5, "Blood");
        ranged.setRuneWeapon("Longbow");
        assertThat(LongRestRules.normalizeRuneWeapon(ranged, existing)).isNull();

        PC noLongerAKnight = deathKnight(5, "Blood");
        noLongerAKnight.setSubclass("Oath of Devotion");
        assertThat(LongRestRules.normalizeRuneWeapon(noLongerAKnight, existing)).isNull();

        PC blank = deathKnight(5, "Blood");
        blank.setRuneWeapon(""); // the sheet clearing it
        assertThat(LongRestRules.normalizeRuneWeapon(blank, existing)).isNull();
    }

    // --- stored choices ---

    @Test
    void choices_roundTripThroughTheSeatColumn() {
        String stored = LongRestRules.serialize(new LongRestChoices(List.of("Bless", "Aid"), " Greatsword "));
        assertThat(stored).isEqualTo("{\"preparedSpells\":[\"Bless\",\"Aid\"],\"runeWeapon\":\"Greatsword\"}");

        LongRestChoices back = LongRestRules.parse(stored);
        assertThat(back.preparedSpells()).containsExactly("Bless", "Aid");
        assertThat(back.runeWeapon()).isEqualTo("Greatsword");
    }

    @Test
    void choices_withNothingChosen_storeAsNull() {
        assertThat(LongRestRules.serialize(null)).isNull();
        assertThat(LongRestRules.serialize(new LongRestChoices(null, null))).isNull();
        assertThat(LongRestRules.serialize(new LongRestChoices(null, "  "))).isNull();
        assertThat(LongRestRules.parse(null)).isNull();
        assertThat(LongRestRules.parse("{}")).isNull();
        // An empty prepared list is a real choice (unprepare everything), not "nothing".
        assertThat(LongRestRules.serialize(new LongRestChoices(List.of(), null)))
                .isEqualTo("{\"preparedSpells\":[]}");
    }
}
