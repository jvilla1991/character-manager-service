package com.moo.charactermanagerservice.progression;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Vestige companion's stat block: the book's numbers for each type, and the cases that pin
 * this builder to the client's ({@code models/vestige-companion.spec.ts} carries the same
 * {@link #SHARED_CASES} block and the same {@link #FIEND_5_JSON}).
 */
class VestigeCompanionTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * {@code type|Warlock level|Charisma|Proficiency Bonus => } Armor Class, Hit Points, Hit Dice,
     * the Pact Bond bonus, Vestige's Strike (to hit, damage), the Divine Power option (with the
     * Hit Points a Healing Touch restores), the resistance and the creature line.
     * Keep this block identical to the one in the client's spec.
     */
    static final String SHARED_CASES = """
            Celestial|3|8|2 => AC 12 ; HP 16 ; 3d6 ; Bond +2 ; Strike +1, 1d6 + 2 Radiant ; Healing Touch 2d8 - 1 ; Resists Radiant damage. ; Small Celestial, Neutral
            Celestial|5|10|3 => AC 13 ; HP 24 ; 5d6 ; Bond +3 ; Strike +3, 1d6 + 3 Radiant ; Healing Touch 2d8 ; Resists Radiant damage. ; Small Celestial, Neutral
            Celestial|20|18|6 => AC 17 ; HP 84 ; 20d6 ; Bond +6 ; Strike +10, 1d6 + 7 Radiant ; Healing Touch 2d8 + 4 ; Resists Radiant damage. ; Small Celestial, Neutral
            Fiend|3|10|2 => AC 13 ; HP 16 ; 3d6 ; Bond +2 ; Strike +2, 1d6 + 3 Fire ; Fiendish Swap ; Resists Fire damage. ; Small Fiend, Neutral
            Fiend|5|18|3 => AC 17 ; HP 24 ; 5d6 ; Bond +3 ; Strike +7, 1d6 + 7 Fire ; Fiendish Swap ; Resists Fire damage. ; Small Fiend, Neutral
            Fiend|20|8|6 => AC 12 ; HP 84 ; 20d6 ; Bond +6 ; Strike +5, 1d6 + 2 Fire ; Fiendish Swap ; Resists Fire damage. ; Small Fiend, Neutral
            Undead|3|18|2 => AC 17 ; HP 16 ; 3d6 ; Bond +2 ; Strike +6, 1d6 + 7 Necrotic ; Cursed Invocation ; Resists Necrotic damage. ; Small Undead, Neutral
            Undead|5|8|3 => AC 12 ; HP 24 ; 5d6 ; Bond +3 ; Strike +2, 1d6 + 2 Necrotic ; Cursed Invocation ; Resists Necrotic damage. ; Small Undead, Neutral
            Undead|20|10|6 => AC 13 ; HP 84 ; 20d6 ; Bond +6 ; Strike +6, 1d6 + 3 Necrotic ; Cursed Invocation ; Resists Necrotic damage. ; Small Undead, Neutral
            Fiend|5|18|4 => AC 17 ; HP 24 ; 5d6 ; Bond +4 ; Strike +8, 1d6 + 7 Fire ; Fiendish Swap ; Resists Fire damage. ; Small Fiend, Neutral
            Undead|9|20|4 => AC 18 ; HP 40 ; 9d6 ; Bond +4 ; Strike +9, 1d6 + 8 Necrotic ; Cursed Invocation ; Resists Necrotic damage. ; Small Undead, Neutral
            Celestial|17|1|6 => AC 8 ; HP 72 ; 17d6 ; Bond +6 ; Strike +1, 1d6 - 2 Radiant ; Healing Touch 2d8 - 5 ; Resists Radiant damage. ; Small Celestial, Neutral
            Fiend|3|30|2 => AC 23 ; HP 16 ; 3d6 ; Bond +2 ; Strike +12, 1d6 + 13 Fire ; Fiendish Swap ; Resists Fire damage. ; Small Fiend, Neutral
            Celestial|12|16|5 => AC 16 ; HP 52 ; 12d6 ; Bond +5 ; Strike +8, 1d6 + 6 Radiant ; Healing Touch 2d8 + 3 ; Resists Radiant damage. ; Small Celestial, Neutral
            """;

    /**
     * The whole block for a Fiend vestige of a Warlock 5 with Charisma 18, Proficiency Bonus +3,
     * who knows Common and Infernal: key for key, in order. The client's spec holds the same text.
     */
    static final String FIEND_5_JSON = "{\"companion\":\"vestige\",\"clazz\":\"Vestige\",\"race\":\"Fiend\",\"level\":5,"
            + "\"alignment\":\"Neutral\",\"hp\":{\"max\":24,\"cur\":24,\"temp\":0},\"ac\":17,\"init\":2,\"speed\":5,"
            + "\"prof\":3,\"stats\":{\"STR\":1,\"DEX\":14,\"CON\":10,\"INT\":15,\"WIS\":15,\"CHA\":16},\"saves\":[],"
            + "\"skills\":{},\"conditions\":[],\"conditionImmunities\":[\"charmed\",\"frightened\",\"prone\"],"
            + "\"coins\":{\"cp\":0,\"sp\":0,\"gp\":0},\"spellSlots\":{},\"spells\":[],\"inventory\":[],\"features\":["
            + "{\"name\":\"Pact Bond\",\"source\":\"Trait\",\"desc\":\"Adds +3 (your Proficiency Bonus) to every "
            + "ability check and saving throw it makes.\"},"
            + "{\"name\":\"Vestige's Strike\",\"source\":\"Action\",\"desc\":\"Melee or ranged attack: +7 to hit, "
            + "reach 5 ft. or range 60 ft. Hit: 1d6 + 7 Fire damage.\"},"
            + "{\"name\":\"Divine Power: Fiendish Swap\",\"source\":\"Bonus Action\",\"desc\":\"While you and the "
            + "vestige are within 60 ft. of each other, you teleport into each other's space. One use, counted as "
            + "Divine Power on your sheet.\"},"
            + "{\"name\":\"Creature\",\"source\":\"Stat block\",\"desc\":\"Small Fiend, Neutral\",\"category\":\"other\"},"
            + "{\"name\":\"Movement\",\"source\":\"Stat block\",\"desc\":\"Fly 30 ft. (hover)\",\"category\":\"other\"},"
            + "{\"name\":\"Resistance\",\"source\":\"Stat block\",\"desc\":\"Fire damage.\",\"category\":\"other\"},"
            + "{\"name\":\"Hit Dice\",\"source\":\"Stat block\",\"desc\":\"5d6\",\"category\":\"other\"}],"
            + "\"languages\":[\"Common\",\"Infernal\"],\"toolProfs\":[],\"bio\":\"The vestige of a dying god, bound to "
            + "you by your pact. It acts on your turn: it moves and uses its Reaction by itself, but only Dodges "
            + "unless you command it with a Bonus Action or give up one attack of your Attack action for its "
            + "Vestige's Strike. At 0 Hit Points it disappears until you perform a 1-minute ceremony, which brings "
            + "it back with all its Hit Points.\"}";

    private static final Pattern BOND = Pattern.compile("Adds ([+-]\\d+) ");
    private static final Pattern STRIKE =
            Pattern.compile(": ([+-]\\d+) to hit, reach 5 ft\\. or range 60 ft\\. Hit: (.+) damage\\.");
    private static final Pattern HEAL = Pattern.compile("regains (\\S+(?: [+-] \\d+)?) Hit Points");

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> features(Map<String, Object> block) {
        return (List<Map<String, Object>>) block.get("features");
    }

    private static Map<String, Object> feature(Map<String, Object> block, String namePrefix) {
        return features(block).stream()
                .filter(f -> String.valueOf(f.get("name")).startsWith(namePrefix)).findFirst().orElseThrow();
    }

    private static String desc(Map<String, Object> block, String namePrefix) {
        return String.valueOf(feature(block, namePrefix).get("desc"));
    }

    private static String group(Pattern pattern, String text, int group) {
        Matcher m = pattern.matcher(text);
        assertThat(m.find()).as(text).isTrue();
        return m.group(group);
    }

    /** One block as a shared case reads it; the client's spec has the same function. */
    private static String summary(Map<String, Object> block) {
        Map<String, Object> power = feature(block, "Divine Power: ");
        String powerName = String.valueOf(power.get("name")).substring("Divine Power: ".length());
        Matcher heal = HEAL.matcher(String.valueOf(power.get("desc")));
        String strike = desc(block, "Vestige's Strike");
        return String.join(" ; ",
                "AC " + block.get("ac"),
                "HP " + ((Map<?, ?>) block.get("hp")).get("max"),
                desc(block, "Hit Dice"),
                "Bond " + group(BOND, desc(block, "Pact Bond"), 1),
                "Strike " + group(STRIKE, strike, 1) + ", " + group(STRIKE, strike, 2),
                powerName + (heal.find() ? " " + heal.group(1) : ""),
                "Resists " + desc(block, "Resistance"),
                desc(block, "Creature"));
    }

    @Test
    void sharedCases_matchTheClientsBuilder() {
        List<String> lines = SHARED_CASES.lines().filter(l -> !l.isBlank()).toList();
        assertThat(lines).hasSize(14);
        for (String line : lines) {
            String[] sides = line.split(" => ");
            String[] in = sides[0].split("\\|");
            Map<String, Object> block = VestigeCompanion.statBlock(in[0], Integer.parseInt(in[1]),
                    Integer.parseInt(in[2]), Integer.parseInt(in[3]), List.of());
            assertThat(summary(block)).as(sides[0]).isEqualTo(sides[1]);
            assertThat(block.get("prof")).as(sides[0]).isEqualTo(Integer.parseInt(in[3]));
            assertThat(block.get("level")).as(sides[0]).isEqualTo(Integer.parseInt(in[1]));
            assertThat(block.get("race")).as(sides[0]).isEqualTo(in[0]);
        }
    }

    @Test
    void theWholeBlock_isTheSameJsonTheClientBuilds() throws Exception {
        Map<String, Object> block = VestigeCompanion.statBlock("Fiend", 5, 18, 3, List.of("Common", "Infernal"));
        assertThat(JSON.writeValueAsString(block)).isEqualTo(FIEND_5_JSON);
    }

    @Test
    void theBooksFixedNumbers_areOnEveryBlock() {
        for (String type : SubclassChoices.VESTIGE_TYPES) {
            Map<String, Object> block = VestigeCompanion.statBlock(type, 7, 14, 3, List.of("Common"));
            assertThat(block).containsEntry("companion", "vestige")
                    .containsEntry("clazz", "Vestige")
                    .containsEntry("alignment", "Neutral")
                    .containsEntry("init", 2)
                    .containsEntry("speed", 5)
                    .containsEntry("stats", Map.of("STR", 1, "DEX", 14, "CON", 10, "INT", 15, "WIS", 15, "CHA", 16))
                    .containsEntry("conditionImmunities", List.of("charmed", "frightened", "prone"))
                    .containsEntry("conditions", List.of())
                    .containsEntry("languages", List.of("Common"));
            // Hit Points 4 + four times the Warlock level, all of them there, none temporary.
            assertThat(block.get("hp")).isEqualTo(Map.of("max", 32, "cur", 32, "temp", 0));
            // Armor Class 13 + the warlock's Charisma modifier.
            assertThat(block.get("ac")).isEqualTo(15);
            assertThat(desc(block, "Movement")).isEqualTo("Fly 30 ft. (hover)");
            assertThat(features(block)).extracting(f -> f.get("name")).containsExactly("Pact Bond",
                    "Vestige's Strike", String.valueOf(feature(block, "Divine Power: ").get("name")),
                    "Creature", "Movement", "Resistance", "Hit Dice");
            assertThat(features(block)).extracting(f -> f.get("source")).containsExactly("Trait", "Action",
                    "Bonus Action", "Stat block", "Stat block", "Stat block", "Stat block");
            // The four facts sit under Other Features; the trait and the actions do not.
            assertThat(features(block)).extracting(f -> f.get("category"))
                    .containsExactly(null, null, null, "other", "other", "other", "other");
        }
    }

    @Test
    void eachType_hasItsDamageItsResistanceAndItsOneDivinePower() {
        Map<String, String> damage = Map.of("Celestial", "Radiant", "Fiend", "Fire", "Undead", "Necrotic");
        Map<String, String> power = Map.of("Celestial", "Divine Power: Healing Touch",
                "Fiend", "Divine Power: Fiendish Swap", "Undead", "Divine Power: Cursed Invocation");
        for (String type : SubclassChoices.VESTIGE_TYPES) {
            Map<String, Object> block = VestigeCompanion.statBlock(type, 3, 16, 2, List.of());
            assertThat(desc(block, "Vestige's Strike")).endsWith("Hit: 1d6 + 6 " + damage.get(type) + " damage.");
            assertThat(desc(block, "Resistance")).isEqualTo(damage.get(type) + " damage.");
            assertThat(desc(block, "Creature")).isEqualTo("Small " + type + ", Neutral");
            assertThat(features(block)).extracting(f -> f.get("name"))
                    .filteredOn(n -> String.valueOf(n).startsWith("Divine Power")).containsExactly(power.get(type));
            assertThat(block.get("race")).isEqualTo(type);
        }
    }

    @Test
    void charisma_movesArmorClassTheAttackTheDamageAndTheHealing() {
        // Charisma 8 (-1), 10 (+0) and 18 (+4), each with Proficiency Bonus +2.
        Map<String, Object> low = VestigeCompanion.statBlock("Celestial", 3, 8, 2, List.of());
        Map<String, Object> even = VestigeCompanion.statBlock("Celestial", 3, 10, 2, List.of());
        Map<String, Object> high = VestigeCompanion.statBlock("Celestial", 3, 18, 2, List.of());

        assertThat(List.of(low.get("ac"), even.get("ac"), high.get("ac"))).containsExactly(12, 13, 17);
        assertThat(desc(low, "Vestige's Strike")).contains("+1 to hit").contains("Hit: 1d6 + 2 Radiant");
        assertThat(desc(even, "Vestige's Strike")).contains("+2 to hit").contains("Hit: 1d6 + 3 Radiant");
        assertThat(desc(high, "Vestige's Strike")).contains("+6 to hit").contains("Hit: 1d6 + 7 Radiant");
        assertThat(desc(low, "Divine Power")).contains("regains 2d8 - 1 Hit Points");
        assertThat(desc(even, "Divine Power")).contains("regains 2d8 Hit Points");
        assertThat(desc(high, "Divine Power")).contains("regains 2d8 + 4 Hit Points");
        // Charisma never touches its Hit Points.
        assertThat(low.get("hp")).isEqualTo(high.get("hp"));
    }

    @Test
    void theWarlocksLevel_setsHitPointsAndHitDice() {
        for (int[] row : new int[][] {{3, 16}, {5, 24}, {10, 44}, {20, 84}}) {
            Map<String, Object> block = VestigeCompanion.statBlock("Undead", row[0], 10, 2, List.of());
            assertThat(((Map<?, ?>) block.get("hp")).get("max")).isEqualTo(row[1]);
            assertThat(((Map<?, ?>) block.get("hp")).get("cur")).isEqualTo(row[1]);
            assertThat(desc(block, "Hit Dice")).isEqualTo(row[0] + "d6");
            assertThat(block.get("level")).isEqualTo(row[0]);
        }
    }

    @Test
    void theProficiencyBonus_isTheWarlocks_inThePactBondAndTheAttack() {
        // The same warlock with a +1 line on its Proficiency Bonus: +3 becomes +4 everywhere.
        Map<String, Object> plain = VestigeCompanion.statBlock("Fiend", 5, 18, 3, List.of());
        Map<String, Object> blessed = VestigeCompanion.statBlock("Fiend", 5, 18, 4, List.of());

        assertThat(plain.get("prof")).isEqualTo(3);
        assertThat(blessed.get("prof")).isEqualTo(4);
        assertThat(desc(plain, "Pact Bond")).startsWith("Adds +3 (your Proficiency Bonus)");
        assertThat(desc(blessed, "Pact Bond")).startsWith("Adds +4 (your Proficiency Bonus)");
        assertThat(desc(plain, "Vestige's Strike")).contains("+7 to hit");
        assertThat(desc(blessed, "Vestige's Strike")).contains("+8 to hit");
        // The damage bonus is 3 + Charisma: the Proficiency Bonus is no part of it.
        assertThat(desc(blessed, "Vestige's Strike")).contains("Hit: 1d6 + 7 Fire");
    }

    @Test
    void languages_areTheWarlocksOwn_andABlockIsNeverSharedState() {
        List<String> known = List.of("Common", "Abyssal", "Orc");
        Map<String, Object> block = VestigeCompanion.statBlock("Fiend", 3, 10, 2, known);
        assertThat(block.get("languages")).isEqualTo(known);
        assertThat(VestigeCompanion.statBlock("Fiend", 3, 10, 2, null).get("languages")).isEqualTo(List.of());

        // Each call builds its own lists: changing one block leaves the next one whole.
        @SuppressWarnings("unchecked")
        List<String> immunities = (List<String>) block.get("conditionImmunities");
        immunities.clear();
        assertThat(VestigeCompanion.statBlock("Fiend", 3, 10, 2, known).get("conditionImmunities"))
                .isEqualTo(List.of("charmed", "frightened", "prone"));
    }

    @Test
    void typeFor_isSetOnlyForAVestigePatronOfWarlockThreeWithATypeChosen() {
        String fiend = "{\"vestigeType\":\"Fiend\",\"vestigeDomain\":\"War\"}";
        assertThat(VestigeCompanion.typeFor("Warlock", "Vestige Patron", 3, fiend)).isEqualTo("Fiend");
        assertThat(VestigeCompanion.typeFor("warlock", " vestige patron ", 20, "{\"vestigeType\":\"undead\"}"))
                .isEqualTo("Undead");

        assertThat(VestigeCompanion.typeFor("Warlock", "Vestige Patron", 2, fiend)).isNull();
        assertThat(VestigeCompanion.typeFor("Warlock", "Vestige Patron", 5, null)).isNull();
        assertThat(VestigeCompanion.typeFor("Warlock", "Vestige Patron", 5, "{\"vestigeDomain\":\"War\"}")).isNull();
        assertThat(VestigeCompanion.typeFor("Warlock", "Vestige Patron", 5, "{\"vestigeType\":\"Dragon\"}")).isNull();
        assertThat(VestigeCompanion.typeFor("Warlock", "Fiend Patron", 5, fiend)).isNull();
        assertThat(VestigeCompanion.typeFor("Warlock", null, 5, fiend)).isNull();
        assertThat(VestigeCompanion.typeFor("Wizard", "Vestige Patron", 5, fiend)).isNull();
        assertThat(VestigeCompanion.typeFor(null, null, 5, fiend)).isNull();
    }

    @Test
    void isPatron_looksOnlyAtTheClassAndTheSubclass() {
        assertThat(VestigeCompanion.isPatron("Warlock", "Vestige Patron")).isTrue();
        assertThat(VestigeCompanion.isPatron("warlock", "VESTIGE PATRON")).isTrue();
        assertThat(VestigeCompanion.isPatron("Warlock", "Fiend Patron")).isFalse();
        assertThat(VestigeCompanion.isPatron("Warlock", null)).isFalse();
        assertThat(VestigeCompanion.isPatron("Fighter", "Vestige Patron")).isFalse();
        assertThat(VestigeCompanion.isPatron(null, "Vestige Patron")).isFalse();
    }

    @Test
    void isVestige_readsTheMarker() {
        assertThat(VestigeCompanion.isVestige(VestigeCompanion.statBlock("Fiend", 3, 10, 2, List.of()))).isTrue();
        assertThat(VestigeCompanion.isVestige(Map.of("clazz", "Wolf"))).isFalse();
        assertThat(VestigeCompanion.isVestige(Map.of("companion", "pet"))).isFalse();
        assertThat(VestigeCompanion.isVestige(null)).isFalse();
    }
}
