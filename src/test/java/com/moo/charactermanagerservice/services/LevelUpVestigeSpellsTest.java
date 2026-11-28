package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.HpMode;
import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.dto.SpellGrant;
import com.moo.charactermanagerservice.models.PC;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A Vestige Patron's Vestige Spells at a level-up: what the preview says each domain would hand
 * over while none is chosen, what the character is owed once one is, and what a commit saves.
 */
class LevelUpVestigeSpellsTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VESTIGE_SPELLS = "Vestige Spells";

    private final LevelUpService service = new LevelUpService(JSON);

    private static PC warlock(String subclass, int level, String choices) {
        PC pc = new PC();
        pc.setClazz("Warlock");
        pc.setSubclass(subclass);
        pc.setLevel((short) level);
        pc.setAbilityCon((short) 14);
        pc.setAbilityCha((short) 16);
        pc.setHpMax((short) 30);
        pc.setHpCurrent((short) 30);
        pc.setSubclassChoices(choices);
        return pc;
    }

    private static PC vestige(int level, String choices) {
        return warlock("Vestige Patron", level, choices);
    }

    private static String choices(String type, String domain) {
        return "{\"vestigeType\":\"" + type + "\",\"vestigeDomain\":\"" + domain + "\"}";
    }

    private static Map<String, Object> spell(int level, String name) {
        Map<String, Object> spell = new LinkedHashMap<>();
        spell.put("lvl", level);
        spell.put("name", name);
        spell.put("prepared", true);
        return spell;
    }

    private static String held(int level, String name, boolean alwaysPrepared) {
        return "{\"lvl\":" + level + ",\"name\":\"" + name + "\",\"prepared\":true"
                + (alwaysPrepared ? ",\"alwaysPrepared\":true,\"grantedBy\":\"" + VESTIGE_SPELLS + "\"" : "") + "}";
    }

    private static List<Map<String, Object>> spellsOf(PC pc) {
        try {
            return pc.getSpells() == null ? List.of() : JSON.readValue(pc.getSpells(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> saved(PC pc, String name) {
        return spellsOf(pc).stream().filter(s -> name.equals(s.get("name"))).findFirst().orElseThrow();
    }

    private static List<String> names(List<SpellGrant> grants) {
        return grants.stream().map(SpellGrant::name).toList();
    }

    private PC levelUp(PC pc, List<Map<String, Object>> newSpells, Map<String, Object> subclassChoices) {
        return service.applyLevelUp(pc, null, null, null, newSpells, HpMode.AVERAGE, null, null,
                subclassChoices, null);
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

    // ── Preview ──────────────────────────────────────────────────────────────────

    @Test
    void preview_reachingWarlockThree_listsWhatEachDomainWouldHandOver() {
        LevelUpPreview preview = service.preview(vestige(2, null));

        assertThat(preview.alwaysPreparedSpells()).isEmpty();
        Map<String, List<SpellGrant>> byDomain = preview.alwaysPreparedSpellsByVestigeDomain();
        assertThat(byDomain.keySet()).containsExactly("Life", "Light", "Trickery", "War");
        assertThat(names(byDomain.get("Life"))).containsExactly("Aid", "Bless", "Cure Wounds", "Lesser Restoration");
        assertThat(names(byDomain.get("Light")))
                .containsExactly("Burning Hands", "Faerie Fire", "Scorching Ray", "See Invisibility");
        assertThat(names(byDomain.get("Trickery")))
                .containsExactly("Charm Person", "Disguise Self", "Invisibility", "Pass without Trace");
        assertThat(names(byDomain.get("War")))
                .containsExactly("Guiding Bolt", "Magic Weapon", "Shield of Faith", "Spiritual Weapon");
        byDomain.values().forEach(grants ->
                assertThat(grants).extracting(SpellGrant::grantedBy).containsOnly(VESTIGE_SPELLS));
    }

    @Test
    void preview_field_isAnObjectOfNameAndGrantedByLists() throws Exception {
        String json = JSON.writeValueAsString(service.preview(vestige(2, null)));
        Map<String, Object> read = JSON.readValue(json, new TypeReference<>() {});

        assertThat(read).containsKey("alwaysPreparedSpellsByVestigeDomain");
        assertThat(JSON.writeValueAsString(
                ((Map<?, ?>) read.get("alwaysPreparedSpellsByVestigeDomain")).get("War")))
                .isEqualTo("[{\"name\":\"Guiding Bolt\",\"grantedBy\":\"Vestige Spells\"},"
                        + "{\"name\":\"Magic Weapon\",\"grantedBy\":\"Vestige Spells\"},"
                        + "{\"name\":\"Shield of Faith\",\"grantedBy\":\"Vestige Spells\"},"
                        + "{\"name\":\"Spiritual Weapon\",\"grantedBy\":\"Vestige Spells\"}]");
    }

    @Test
    void preview_belowWarlockThree_orForAnotherPatron_listsNoDomain() {
        assertThat(service.preview(vestige(1, null)).alwaysPreparedSpellsByVestigeDomain()).isEmpty();
        assertThat(service.preview(warlock("Fiend Patron", 2, null)).alwaysPreparedSpellsByVestigeDomain())
                .isEmpty();
        // A stale choice on another patron is not a domain.
        assertThat(service.preview(warlock("Fiend Patron", 4, choices("Fiend", "War"))).alwaysPreparedSpells())
                .extracting(SpellGrant::grantedBy).doesNotContain(VESTIGE_SPELLS);

        PC cleric = new PC();
        cleric.setClazz("Cleric");
        cleric.setSubclass("Life Domain");
        cleric.setLevel((short) 4);
        cleric.setHpMax((short) 20);
        assertThat(service.preview(cleric).alwaysPreparedSpellsByVestigeDomain()).isEmpty();
    }

    @Test
    void preview_aDomainStillUnsetAtAHigherLevel_listsEveryRowTheLevelHasPassed() {
        // The DM set the subclass by hand at Warlock 7: the level-up to 8 asks for the domain.
        LevelUpPreview preview = service.preview(vestige(7, "{\"vestigeType\":\"Fiend\"}"));

        assertThat(names(preview.alwaysPreparedSpellsByVestigeDomain().get("War"))).containsExactly(
                "Guiding Bolt", "Magic Weapon", "Shield of Faith", "Spiritual Weapon",
                "Crusader's Mantle", "Spirit Guardians", "Fire Shield", "Freedom of Movement");
        assertThat(preview.alwaysPreparedSpells()).extracting(SpellGrant::grantedBy).doesNotContain(VESTIGE_SPELLS);
    }

    @Test
    void preview_onceTheDomainIsStored_itsSpellsArePartOfTheOrdinaryList() {
        // Reaching 5 with the level-3 row already held: only the level-5 row is owed.
        PC pc = vestige(4, choices("Fiend", "War"));
        pc.setSpells("[" + held(1, "Guiding Bolt", true) + "," + held(2, "Magic Weapon", true) + ","
                + held(1, "Shield of Faith", true) + "," + held(2, "Spiritual Weapon", true) + "]");
        LevelUpPreview preview = service.preview(pc);

        assertThat(preview.alwaysPreparedSpells()).containsExactly(
                new SpellGrant("Crusader's Mantle", VESTIGE_SPELLS),
                new SpellGrant("Spirit Guardians", VESTIGE_SPELLS));
        assertThat(preview.alwaysPreparedSpellsByVestigeDomain()).isEmpty();
    }

    @Test
    void preview_catchesUpADomainSetOnTheSheet_atTheNextLevelUp() {
        // Domain set through the endpoint at Warlock 9, no spell handed over yet: all ten are owed at 10.
        // (Contact Other Plane is the Warlock's own level-9 grant, also missing here.)
        LevelUpPreview preview = service.preview(vestige(9, choices("Undead", "Life")));

        assertThat(names(preview.alwaysPreparedSpells())).containsExactly("Contact Other Plane",
                "Aid", "Bless", "Cure Wounds", "Lesser Restoration", "Mass Healing Word", "Revivify",
                "Aura of Life", "Death Ward", "Greater Restoration", "Mass Cure Wounds");
    }

    @Test
    void preview_aSpellTheCharacterMerelyKnows_isStillOwed_aHeldOneIsNot() {
        PC pc = vestige(2, null);
        pc.setSpells("[" + held(1, "Charm Person", false) + "]");
        assertThat(names(service.preview(pc).alwaysPreparedSpellsByVestigeDomain().get("Trickery")))
                .contains("Charm Person");

        pc.setSpells("[" + held(1, "Charm Person", true) + "]");
        assertThat(names(service.preview(pc).alwaysPreparedSpellsByVestigeDomain().get("Trickery")))
                .containsExactly("Disguise Self", "Invisibility", "Pass without Trace");
    }

    // ── Commit ───────────────────────────────────────────────────────────────────

    @Test
    void levelUp_toThree_savesTheChosenDomainsSpells_alwaysPrepared_outsideThePicks() {
        // Warlock 2 -> 3 learns one spell of its own; the four Life spells ride along.
        PC pc = vestige(2, null);
        levelUp(pc, List.of(spell(2, "Hold Person"), spell(2, "Aid"), spell(1, "Bless"),
                        spell(1, "Cure Wounds"), spell(2, "Lesser Restoration")),
                Map.of("vestigeType", "Celestial", "vestigeDomain", "Life"));

        assertThat(pc.getLevel()).isEqualTo((short) 3);
        for (String name : List.of("Aid", "Bless", "Cure Wounds", "Lesser Restoration")) {
            assertThat(saved(pc, name)).containsEntry("alwaysPrepared", true)
                    .containsEntry("prepared", true).containsEntry("grantedBy", VESTIGE_SPELLS);
        }
        assertThat(saved(pc, "Hold Person")).doesNotContainKey("alwaysPrepared").doesNotContainKey("grantedBy");
    }

    @Test
    void levelUp_onlyTheChosenDomainsSpells_areGrants() {
        // Bless is a Life spell: for a War vestige it is an ordinary pick, and one too many.
        assertRefused(() -> levelUp(vestige(2, null),
                        List.of(spell(2, "Hold Person"), spell(1, "Bless"), spell(1, "Guiding Bolt")),
                        Map.of("vestigeType", "Fiend", "vestigeDomain", "War")),
                "You can learn at most 1 new spell(s) at level 3");
    }

    @Test
    void levelUp_aGrantAboveThePactSlotLevel_isStillSaved() {
        // Warlock 8 -> 9 has level-5 pact slots; the level-5 row would pass anyway. Check the
        // catch-up instead: a domain chosen late hands over every row at once, whatever its level.
        PC pc = vestige(8, "{\"vestigeType\":\"Fiend\"}");
        levelUp(pc, List.of(spell(1, "Guiding Bolt"), spell(3, "Spirit Guardians"), spell(5, "Hold Monster")),
                Map.of("vestigeDomain", "War"));

        for (String name : List.of("Guiding Bolt", "Spirit Guardians", "Hold Monster")) {
            assertThat(saved(pc, name)).containsEntry("alwaysPrepared", true)
                    .containsEntry("grantedBy", VESTIGE_SPELLS);
        }
    }

    @Test
    void levelUp_marksASpellTheCharacterAlreadyKnows_insteadOfAddingItTwice() {
        PC pc = vestige(4, choices("Fiend", "Light"));
        pc.setSpells("[" + held(3, "Fireball", false) + "]");
        levelUp(pc, List.of(spell(3, "Fireball"), spell(3, "Daylight")), null);

        assertThat(spellsOf(pc)).extracting(s -> s.get("name")).containsExactly("Fireball", "Daylight");
        assertThat(saved(pc, "Fireball")).containsEntry("alwaysPrepared", true)
                .containsEntry("grantedBy", VESTIGE_SPELLS);
    }

    @Test
    void levelUp_withoutTheSpells_stillGoesThrough_andTheyStayOwed() {
        PC pc = vestige(2, null);
        levelUp(pc, null, Map.of("vestigeType", "Undead", "vestigeDomain", "Trickery"));

        assertThat(pc.getLevel()).isEqualTo((short) 3);
        assertThat(pc.getSpells()).isNull();
        assertThat(names(service.preview(pc).alwaysPreparedSpells()))
                .containsExactly("Charm Person", "Disguise Self", "Invisibility", "Pass without Trace");
    }

    @Test
    void levelUp_anotherPatron_treatsADomainSpellAsAnOrdinaryPick() {
        PC fiend = warlock("Fiend Patron", 2, null);
        levelUp(fiend, List.of(spell(1, "Bless")), null);

        assertThat(saved(fiend, "Bless")).doesNotContainKey("alwaysPrepared").doesNotContainKey("grantedBy");
    }
}
