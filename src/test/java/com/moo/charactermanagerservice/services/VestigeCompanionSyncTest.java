package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.FollowerKind;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcFollower;
import com.moo.charactermanagerservice.progression.VestigeCompanion;
import com.moo.charactermanagerservice.repositories.PcFollowerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The one step that creates, rebuilds and removes a Vestige Patron's companion. The follower
 * table is a list here: the step is run against it the way {@link PCService} runs it after a
 * save, and the list is what the character's followers would then be.
 */
@ExtendWith(MockitoExtension.class)
class VestigeCompanionSyncTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private PcFollowerRepository followerRepository;

    private VestigeCompanionSync sync;
    private final List<PcFollower> table = new ArrayList<>();
    private long nextId = 100;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        sync = new VestigeCompanionSync(followerRepository, JSON);
        ownerId = UUID.randomUUID();
        lenient().when(followerRepository.findByPcIdAndKind(any(), any())).thenAnswer(inv -> table.stream()
                .filter(f -> inv.getArgument(0).equals(f.getPcId()) && f.getKind() == inv.getArgument(1))
                .toList());
        lenient().when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> {
            PcFollower follower = inv.getArgument(0);
            if (follower.getId() == null) follower.setId(nextId++);
            if (!table.contains(follower)) table.add(follower);
            return follower;
        });
        lenient().doAnswer(inv -> {
            List<PcFollower> gone = new ArrayList<>(inv.<List<PcFollower>>getArgument(0));
            table.removeAll(gone);
            return null;
        }).when(followerRepository).deleteAll(anyList());
    }

    private PC warlock(String subclass, int level, int charisma, String choices) {
        PC pc = new PC();
        pc.setId(1L);
        pc.setUserId(ownerId);
        pc.setClazz("Warlock");
        pc.setSubclass(subclass);
        pc.setLevel((short) level);
        pc.setAbilityCha((short) charisma);
        pc.setSubclassChoices(choices);
        pc.setLanguages("[\"Common\",\"Infernal\"]");
        return pc;
    }

    private PC vestigePatron(int level, int charisma, String type) {
        return warlock("Vestige Patron", level, charisma,
                type == null ? null : "{\"vestigeType\":\"" + type + "\",\"vestigeDomain\":\"War\"}");
    }

    private PcFollower other(long id, String name, FollowerKind kind, String block) {
        PcFollower follower = new PcFollower();
        follower.setId(id);
        follower.setPcId(1L);
        follower.setName(name);
        follower.setKind(kind);
        follower.setStatBlock(block);
        table.add(follower);
        return follower;
    }

    private static Map<String, Object> block(PcFollower follower) {
        try {
            return JSON.readValue(follower.getStatBlock(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private PcFollower vestige() {
        List<PcFollower> found = table.stream().filter(f -> f.getKind() == FollowerKind.COMPANION
                && VestigeCompanion.isVestige(block(f))).toList();
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    @SuppressWarnings("unchecked")
    private static String feature(Map<String, Object> block, String namePrefix) {
        return ((List<Map<String, Object>>) block.get("features")).stream()
                .filter(f -> String.valueOf(f.get("name")).startsWith(namePrefix))
                .map(f -> String.valueOf(f.get("desc"))).findFirst().orElseThrow();
    }

    /** Set what play changes on the stored vestige: current and temporary Hit Points, conditions, its name. */
    private void play(String name, int cur, int temp, List<String> conditions) {
        PcFollower vestige = vestige();
        Map<String, Object> block = block(vestige);
        Map<String, Object> hp = new java.util.LinkedHashMap<>((Map<String, Object>) block.get("hp"));
        hp.put("cur", cur);
        hp.put("temp", temp);
        block.put("hp", hp);
        block.put("conditions", conditions);
        vestige.setName(name);
        try {
            vestige.setStatBlock(JSON.writeValueAsString(block));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ── created ──────────────────────────────────────────────────────────────────

    @Test
    void aVestigePatronOfWarlockThreeWithAType_getsItsCompanion() throws Exception {
        PC pc = vestigePatron(3, 16, "Fiend");

        assertThat(sync.sync(pc)).isTrue();

        PcFollower vestige = vestige();
        assertThat(vestige.getName()).isEqualTo("Vestige");
        assertThat(vestige.getKind()).isEqualTo(FollowerKind.COMPANION);
        assertThat(vestige.getPcId()).isEqualTo(1L);
        assertThat(vestige.getGrantedByUserId()).isEqualTo(ownerId);
        // Exactly the builder's block for this warlock: level 3, Charisma 16, +2, its languages.
        assertThat(vestige.getStatBlock()).isEqualTo(JSON.writeValueAsString(
                VestigeCompanion.statBlock("Fiend", 3, 16, 2, List.of("Common", "Infernal"))));
        assertThat(block(vestige).get("hp")).isEqualTo(Map.of("max", 16, "cur", 16, "temp", 0));
        assertThat(table).hasSize(1);
    }

    @Test
    void aSecondRunWithNothingChanged_writesNothing() {
        PC pc = vestigePatron(5, 18, "Undead");
        assertThat(sync.sync(pc)).isTrue();
        String stored = vestige().getStatBlock();

        assertThat(sync.sync(pc)).isFalse();
        assertThat(sync.sync(pc)).isFalse();

        verify(followerRepository).save(any(PcFollower.class)); // the create, and no other
        assertThat(vestige().getStatBlock()).isEqualTo(stored);
        assertThat(table).hasSize(1);
    }

    @Test
    void noCompanion_belowWarlockThree_withoutAType_orForAnyoneElse() {
        List<PC> none = List.of(
                vestigePatron(1, 16, null),
                vestigePatron(2, 16, null),
                // Stored choices cannot make a level 2 warlock eligible.
                vestigePatron(2, 16, "Fiend"),
                // Warlock 3 and later, but the type is not chosen yet (the DM set the subclass by hand).
                vestigePatron(3, 16, null),
                warlock("Vestige Patron", 9, 16, "{\"vestigeDomain\":\"War\"}"),
                warlock("Vestige Patron", 9, 16, "{\"vestigeType\":\"Dragon\"}"),
                // Another patron, even one carrying a stale choice; no patron at all.
                warlock("Fiend Patron", 9, 16, null),
                warlock("Fiend Patron", 9, 16, "{\"vestigeType\":\"Fiend\"}"),
                warlock(null, 9, 16, "{\"vestigeType\":\"Fiend\"}"));
        for (PC pc : none) {
            assertThat(sync.sync(pc)).isFalse();
        }
        PC wizard = vestigePatron(9, 16, "Fiend");
        wizard.setClazz("Wizard");
        assertThat(sync.sync(wizard)).isFalse();

        assertThat(table).isEmpty();
        verify(followerRepository, never()).save(any(PcFollower.class));
        verify(followerRepository, never()).deleteAll(anyList());
    }

    @Test
    void aCharacterNotYetSaved_isLeftAlone() {
        PC unsaved = vestigePatron(3, 16, "Fiend");
        unsaved.setId(null);
        assertThat(sync.sync(unsaved)).isFalse();
        assertThat(sync.sync(null)).isFalse();
        verifyNoInteractions(followerRepository);
    }

    // ── rebuilt ──────────────────────────────────────────────────────────────────

    @Test
    void aLevelUp_rebuildsTheBlock_andGivesTheNewHitPointsAtOnce() {
        sync.sync(vestigePatron(4, 16, "Fiend"));               // 20 Hit Points, Proficiency Bonus +2
        play("Ashmaw", 7, 3, List.of("poisoned"));

        assertThat(sync.sync(vestigePatron(5, 16, "Fiend"))).isTrue();  // 24 Hit Points, +3

        PcFollower vestige = vestige();
        Map<String, Object> block = block(vestige);
        assertThat(vestige.getName()).isEqualTo("Ashmaw");
        assertThat(block.get("hp")).isEqualTo(Map.of("max", 24, "cur", 11, "temp", 3));
        assertThat(block.get("conditions")).isEqualTo(List.of("poisoned"));
        assertThat(block.get("level")).isEqualTo(5);
        assertThat(block.get("prof")).isEqualTo(3);
        assertThat(feature(block, "Hit Dice")).isEqualTo("5d6");
        assertThat(feature(block, "Vestige's Strike")).contains("+6 to hit");
        assertThat(feature(block, "Pact Bond")).startsWith("Adds +3 ");
        assertThat(table).hasSize(1);
    }

    @Test
    void aCharismaChange_movesArmorClassAndTheAttack_notTheHitPoints() {
        sync.sync(vestigePatron(5, 16, "Celestial"));
        play("Vestige", 10, 0, List.of());

        assertThat(sync.sync(vestigePatron(5, 18, "Celestial"))).isTrue();

        Map<String, Object> block = block(vestige());
        assertThat(block.get("ac")).isEqualTo(17);
        assertThat(feature(block, "Vestige's Strike")).contains("+7 to hit").contains("1d6 + 7 Radiant");
        assertThat(feature(block, "Divine Power")).contains("2d8 + 4");
        assertThat(block.get("hp")).isEqualTo(Map.of("max", 24, "cur", 10, "temp", 0));

        // And back down again: Charisma 8 is a -1.
        assertThat(sync.sync(vestigePatron(5, 8, "Celestial"))).isTrue();
        assertThat(block(vestige()).get("ac")).isEqualTo(12);
    }

    @Test
    void aSheetThatNeverStoredCharisma_countsAsTen() {
        PC pc = vestigePatron(3, 10, "Fiend");
        pc.setAbilityCha(null);
        sync.sync(pc);
        assertThat(block(vestige()).get("ac")).isEqualTo(13);
    }

    @Test
    void theDmsProficiencyBonusLines_countInThePactBondAndTheAttack() {
        PC pc = vestigePatron(5, 18, "Fiend");
        sync.sync(pc);
        assertThat(block(vestige()).get("prof")).isEqualTo(3);

        pc.setStatAdjustments("{\"prof\":[{\"value\":1,\"label\":\"Blessing\"}]}");
        assertThat(sync.sync(pc)).isTrue();

        Map<String, Object> block = block(vestige());
        assertThat(block.get("prof")).isEqualTo(4);
        assertThat(feature(block, "Pact Bond")).startsWith("Adds +4 ");
        assertThat(feature(block, "Vestige's Strike")).contains("+8 to hit").contains("1d6 + 7 Fire");
        // The bonus the body of a save happens to carry is not what is read.
        pc.setProfBonus((short) 9);
        assertThat(sync.sync(pc)).isFalse();
    }

    @Test
    void aTypeChange_rebuildsEverythingButTheNameTheHitPointsAndTheConditions() {
        sync.sync(vestigePatron(5, 18, "Fiend"));
        play("Ashmaw", 9, 4, List.of("poisoned", "restrained"));

        assertThat(sync.sync(vestigePatron(5, 18, "Undead"))).isTrue();

        PcFollower vestige = vestige();
        Map<String, Object> block = block(vestige);
        assertThat(vestige.getName()).isEqualTo("Ashmaw");
        assertThat(block.get("hp")).isEqualTo(Map.of("max", 24, "cur", 9, "temp", 4));
        assertThat(block.get("conditions")).isEqualTo(List.of("poisoned", "restrained"));
        assertThat(block.get("race")).isEqualTo("Undead");
        assertThat(feature(block, "Creature")).isEqualTo("Small Undead, Neutral");
        assertThat(feature(block, "Resistance")).isEqualTo("Necrotic damage.");
        assertThat(feature(block, "Vestige's Strike")).contains("Necrotic damage");
        assertThat(feature(block, "Divine Power: Cursed Invocation")).contains("Disadvantage");
        assertThat(vestige.getStatBlock()).doesNotContain("Fiendish Swap").doesNotContain("Fire");
        // The same follower row, not a new one.
        assertThat(table).hasSize(1);
        verify(followerRepository, never()).deleteAll(anyList());
    }

    @Test
    void aNewLanguage_reachesTheVestige() {
        PC pc = vestigePatron(3, 16, "Fiend");
        sync.sync(pc);
        pc.setLanguages("[\"Common\",\"Infernal\",\"Draconic\"]");

        assertThat(sync.sync(pc)).isTrue();
        assertThat(block(vestige()).get("languages")).isEqualTo(List.of("Common", "Infernal", "Draconic"));

        // An unreadable or missing list is no languages, never a failed save.
        pc.setLanguages("not json");
        assertThat(sync.sync(pc)).isTrue();
        assertThat(block(vestige()).get("languages")).isEqualTo(List.of());
        pc.setLanguages(null);
        assertThat(sync.sync(pc)).isFalse();
    }

    @Test
    void aLowerLevel_capsTheHitPointsAtTheNewMaximum() {
        sync.sync(vestigePatron(6, 16, "Fiend"));               // 28
        assertThat(sync.sync(vestigePatron(4, 16, "Fiend"))).isTrue();
        assertThat(block(vestige()).get("hp")).isEqualTo(Map.of("max", 20, "cur", 20, "temp", 0));
    }

    @Test
    void whateverSomeoneWroteIntoTheStoredBlock_isRebuiltAway() {
        sync.sync(vestigePatron(5, 18, "Fiend"));
        PcFollower vestige = vestige();
        vestige.setStatBlock(vestige.getStatBlock().replace("\"ac\":17", "\"ac\":40")
                .replace("\"spells\":[]", "\"spells\":[{\"name\":\"Wish\",\"lvl\":9}]"));

        assertThat(sync.sync(vestigePatron(5, 18, "Fiend"))).isTrue();

        assertThat(block(vestige()).get("ac")).isEqualTo(17);
        assertThat(block(vestige()).get("spells")).isEqualTo(List.of());
    }

    // ── removed ──────────────────────────────────────────────────────────────────

    @Test
    void theVestigeGoes_whenTheSubclassChanges_theLevelDropsBelowThree_orTheTypeIsGone() {
        List<PC> lost = List.of(
                warlock("Fiend Patron", 5, 16, null),
                warlock(null, 5, 16, null),
                vestigePatron(2, 16, null),
                vestigePatron(5, 16, null));
        for (PC after : lost) {
            table.clear();
            sync.sync(vestigePatron(5, 16, "Fiend"));
            assertThat(table).hasSize(1);

            assertThat(sync.sync(after)).isTrue();
            assertThat(table).isEmpty();
            // Gone is gone: the next save has nothing left to do.
            assertThat(sync.sync(after)).isFalse();
        }
        PC fighter = vestigePatron(5, 16, "Fiend");
        sync.sync(fighter);
        fighter.setClazz("Fighter");
        fighter.setSubclass("Champion");
        assertThat(sync.sync(fighter)).isTrue();
        assertThat(table).isEmpty();
    }

    // ── nothing else is ever touched ─────────────────────────────────────────────

    @Test
    void aGrantASummonAFamiliarAndAnotherCompanion_areNeverReadChangedOrRemoved() {
        String guard = "{\"clazz\":\"Guard\",\"ac\":16,\"hp\":{\"max\":11,\"cur\":4,\"temp\":0}}";
        PcFollower grant = other(5L, "Brannoc", FollowerKind.DM_GRANT, guard);
        PcFollower summon = other(6L, "Thorn", FollowerKind.SUMMON, guard);
        PcFollower familiar = other(7L, "Pip", FollowerKind.FAMILIAR, guard);
        // A companion some other feature owns: the kind matches, the marker does not.
        PcFollower pet = other(8L, "Fang", FollowerKind.COMPANION, "{\"companion\":\"pet\",\"clazz\":\"Wolf\"}");
        PcFollower unmarked = other(9L, "Old", FollowerKind.COMPANION, guard);
        List<PcFollower> others = List.of(grant, summon, familiar, pet, unmarked);

        // Create beside them, rebuild, change type, then lose the subclass.
        sync.sync(vestigePatron(3, 16, "Fiend"));
        assertThat(table).hasSize(6);
        sync.sync(vestigePatron(4, 18, "Fiend"));
        sync.sync(vestigePatron(4, 18, "Celestial"));
        assertThat(table).hasSize(6);
        sync.sync(warlock("Fiend Patron", 4, 18, null));

        assertThat(table).containsExactlyElementsOf(others);
        assertThat(others).extracting(PcFollower::getName)
                .containsExactly("Brannoc", "Thorn", "Pip", "Fang", "Old");
        assertThat(List.of(grant, summon, familiar, unmarked)).extracting(PcFollower::getStatBlock)
                .containsOnly(guard);
        assertThat(pet.getStatBlock()).isEqualTo("{\"companion\":\"pet\",\"clazz\":\"Wolf\"}");
        // Only companions are ever asked for, and only this character's.
        verify(followerRepository, never()).findByPcIdAndKind(any(), eq(FollowerKind.DM_GRANT));
        verify(followerRepository, never()).findByPcIdAndKind(any(), eq(FollowerKind.SUMMON));
        verify(followerRepository, never()).findByPcIdAndKind(any(), eq(FollowerKind.FAMILIAR));
        verify(followerRepository, never()).findAll();
        verify(followerRepository, never()).countByPcId(any());
    }

    @Test
    void aFiendPatronWithFollowersOfItsOwn_neverGainsOrLosesOne() {
        String guard = "{\"clazz\":\"Guard\",\"ac\":16}";
        other(5L, "Brannoc", FollowerKind.DM_GRANT, guard);
        other(7L, "Pip", FollowerKind.FAMILIAR, guard);

        for (int level : List.of(1, 3, 5, 20)) {
            assertThat(sync.sync(warlock("Fiend Patron", level, 18, null))).isFalse();
        }

        assertThat(table).extracting(PcFollower::getName).containsExactly("Brannoc", "Pip");
        verify(followerRepository, never()).save(any(PcFollower.class));
        verify(followerRepository, never()).deleteAll(anyList());
    }

    @Test
    void shouldASecondVestigeEverExist_theFirstStays() {
        sync.sync(vestigePatron(5, 18, "Fiend"));
        PcFollower first = vestige();
        other(200L, "Twin", FollowerKind.COMPANION, first.getStatBlock());

        assertThat(sync.sync(vestigePatron(5, 18, "Fiend"))).isTrue();

        assertThat(table).containsExactly(first);
    }
}
