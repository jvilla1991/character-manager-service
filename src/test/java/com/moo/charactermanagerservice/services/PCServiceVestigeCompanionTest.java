package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.HpMode;
import com.moo.charactermanagerservice.dto.LevelUpRequest;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.FollowerKind;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcFollower;
import com.moo.charactermanagerservice.models.SessionParticipant;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.progression.VestigeCompanion;
import com.moo.charactermanagerservice.repositories.CampaignRepository;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.PcFollowerRepository;
import com.moo.charactermanagerservice.repositories.PcNoteRepository;
import com.moo.charactermanagerservice.repositories.SessionCombatLogRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Every write of a character that can change its Vestige companion, run through the real
 * {@link PCService} with the real level-up rules and the real companion step: a level-up (the
 * player's and the DM's), a whole-sheet save (the owner's and the DM's) and a subclass-choices
 * write. Only the repositories are stand-ins; the follower table is a list.
 */
@ExtendWith(MockitoExtension.class)
class PCServiceVestigeCompanionTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FIEND_WAR = "{\"vestigeType\":\"Fiend\",\"vestigeDomain\":\"War\"}";

    @Mock
    private PCRepository pcRepository;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private PcNoteRepository pcNoteRepository;
    @Mock
    private PcActivityLogService activityLogService;
    @Mock
    private CombatSessionRepository sessionRepository;
    @Mock
    private SessionParticipantRepository participantRepository;
    @Mock
    private SessionCombatLogRepository combatLogRepository;
    @Mock
    private PcFollowerRepository followerRepository;

    private PCService pcService;
    private final List<PcFollower> table = new ArrayList<>();
    private long nextFollowerId = 100;
    private UUID ownerId;
    private UUID dmId;

    @BeforeEach
    void setUp() {
        pcService = new PCService(pcRepository, new LevelUpService(JSON), campaignRepository, pcNoteRepository,
                activityLogService, sessionRepository, participantRepository,
                new CombatLogService(combatLogRepository, JSON), new VestigeCompanionSync(followerRepository, JSON));
        ownerId = UUID.randomUUID();
        dmId = UUID.randomUUID();

        lenient().when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(followerRepository.findByPcIdAndKind(any(), any())).thenAnswer(inv -> table.stream()
                .filter(f -> inv.getArgument(0).equals(f.getPcId()) && f.getKind() == inv.getArgument(1))
                .toList());
        lenient().when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> {
            PcFollower follower = inv.getArgument(0);
            if (follower.getId() == null) follower.setId(nextFollowerId++);
            if (!table.contains(follower)) table.add(follower);
            return follower;
        });
        lenient().doAnswer(inv -> {
            table.removeAll(new ArrayList<>(inv.<List<PcFollower>>getArgument(0)));
            return null;
        }).when(followerRepository).deleteAll(anyList());
    }

    /** A character as the database holds it; campaign 7 is run by the DM. */
    private PC stored(String clazz, String subclass, int level, int charisma, Long campaignId, String choices) {
        PC pc = sheet(clazz, subclass, level, charisma);
        pc.setName("Aelindra");
        pc.setUserId(ownerId);
        pc.setCampaignId(campaignId);
        pc.setSubclassChoices(choices);
        lenient().when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        if (campaignId != null) {
            Campaign campaign = new Campaign();
            campaign.setId(campaignId);
            campaign.setDmUserId(dmId);
            lenient().when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        }
        return pc;
    }

    /** A whole-sheet body: what the app sends on a save (never the choices, which the server owns). */
    private PC sheet(String clazz, String subclass, int level, int charisma) {
        PC pc = new PC();
        pc.setId(1L);
        pc.setUserId(ownerId);
        pc.setClazz(clazz);
        pc.setSubclass(subclass);
        pc.setLevel((short) level);
        pc.setAbilityCon((short) 12);
        pc.setAbilityCha((short) charisma);
        pc.setHpMax((short) 30);
        pc.setHpCurrent((short) 30);
        pc.setLanguages("[\"Common\"]");
        return pc;
    }

    private static Map<String, Object> block(PcFollower follower) {
        try {
            return JSON.readValue(follower.getStatBlock(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private PcFollower vestige() {
        assertThat(table).hasSize(1);
        PcFollower vestige = table.get(0);
        assertThat(vestige.getKind()).isEqualTo(FollowerKind.COMPANION);
        assertThat(VestigeCompanion.isVestige(block(vestige))).isTrue();
        return vestige;
    }

    /** A vestige already in the table, as the step would have made it, then hurt and renamed. */
    private PcFollower existingVestige(String type, int level, int charisma, int cur) throws Exception {
        Map<String, Object> built = VestigeCompanion.statBlock(type, level, charisma,
                2 + (level - 1) / 4, List.of("Common"));
        ((Map<String, Object>) built.get("hp")).put("cur", cur);
        PcFollower vestige = new PcFollower();
        vestige.setId(50L);
        vestige.setPcId(1L);
        vestige.setName("Ashmaw");
        vestige.setKind(FollowerKind.COMPANION);
        vestige.setStatBlock(JSON.writeValueAsString(built));
        table.add(vestige);
        return vestige;
    }

    private static LevelUpRequest choices(Map<String, Object> subclassChoices) {
        return new LevelUpRequest(null, null, null, null, HpMode.AVERAGE, null, null, subclassChoices, null);
    }

    // ── level-up ─────────────────────────────────────────────────────────────────

    @Test
    void levelUp_toWarlockThree_createsTheVestige_fromTheTypeChosenInTheSameRequest() {
        PC pc = stored("Warlock", "Vestige Patron", 2, 16, null, null);
        pc.setPendingLevelGrant(true);

        pcService.levelUpPC(1L, ownerId, choices(Map.of("vestigeType", "Fiend", "vestigeDomain", "War")));

        PcFollower vestige = vestige();
        assertThat(vestige.getName()).isEqualTo("Vestige");
        assertThat(vestige.getGrantedByUserId()).isEqualTo(ownerId);
        Map<String, Object> block = block(vestige);
        assertThat(block.get("race")).isEqualTo("Fiend");
        assertThat(block.get("level")).isEqualTo(3);
        assertThat(block.get("hp")).isEqualTo(Map.of("max", 16, "cur", 16, "temp", 0));
        assertThat(block.get("ac")).isEqualTo(16);
        assertThat(block.get("languages")).isEqualTo(List.of("Common"));
    }

    @Test
    void levelUp_belowWarlockThree_createsNothing() {
        PC pc = stored("Warlock", "Vestige Patron", 1, 16, null, null);
        pc.setPendingLevelGrant(true);

        pcService.levelUpPC(1L, ownerId, null);

        assertThat(pc.getLevel()).isEqualTo((short) 2);
        assertThat(table).isEmpty();
        verify(followerRepository, never()).save(any(PcFollower.class));
    }

    @Test
    void levelUp_byTheDm_rebuildsTheVestige_andHandsOverTheNewHitPoints() throws Exception {
        stored("Warlock", "Vestige Patron", 4, 16, 7L, FIEND_WAR);
        existingVestige("Fiend", 4, 16, 6);                     // 6 of 20

        pcService.levelUpPCAsDm(1L, dmId, null);                // Warlock 5: 24, Proficiency Bonus +3

        PcFollower vestige = vestige();
        assertThat(vestige.getId()).isEqualTo(50L);
        assertThat(vestige.getName()).isEqualTo("Ashmaw");
        Map<String, Object> block = block(vestige);
        assertThat(block.get("hp")).isEqualTo(Map.of("max", 24, "cur", 10, "temp", 0));
        assertThat(block.get("prof")).isEqualTo(3);
        assertThat(block.get("level")).isEqualTo(5);
    }

    @Test
    void levelUp_withACharismaIncrease_raisesTheVestigesArmorClassInTheSameStep() throws Exception {
        PC pc = stored("Warlock", "Vestige Patron", 3, 16, null, FIEND_WAR);
        pc.setPendingLevelGrant(true);
        existingVestige("Fiend", 3, 16, 16);

        pcService.levelUpPC(1L, ownerId, new LevelUpRequest(null, Map.of("CHA", 2), null, null));

        Map<String, Object> block = block(vestige());
        assertThat(block.get("ac")).isEqualTo(17);              // 13 + 4
        assertThat(block.get("hp")).isEqualTo(Map.of("max", 20, "cur", 20, "temp", 0));
    }

    @Test
    void levelUp_refusedForAMissingType_changesNoFollower() {
        PC pc = stored("Warlock", "Vestige Patron", 2, 16, null, null);
        pc.setPendingLevelGrant(true);

        assertThatThrownBy(() -> pcService.levelUpPC(1L, ownerId, null))
                .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(followerRepository);
    }

    // ── whole-sheet saves ────────────────────────────────────────────────────────

    @Test
    void anOwnersSave_thatChangesCharisma_rebuildsTheVestige() throws Exception {
        stored("Warlock", "Vestige Patron", 5, 16, null, FIEND_WAR);
        existingVestige("Fiend", 5, 16, 9);

        pcService.updatePC(sheet("Warlock", "Vestige Patron", 5, 20), ownerId);

        Map<String, Object> block = block(vestige());
        assertThat(block.get("ac")).isEqualTo(18);
        assertThat(block.get("hp")).isEqualTo(Map.of("max", 24, "cur", 9, "temp", 0));
        assertThat(vestige().getName()).isEqualTo("Ashmaw");
    }

    @Test
    void aSaveThatChangesNothingTheVestigeIsBuiltFrom_readsItAndWritesNothing() throws Exception {
        stored("Warlock", "Vestige Patron", 5, 16, null, FIEND_WAR);
        PcFollower vestige = existingVestige("Fiend", 5, 16, 9);
        String before = vestige.getStatBlock();

        PC body = sheet("Warlock", "Vestige Patron", 5, 16);
        body.setHpCurrent((short) 12);
        body.setNotes("Met the vestige's old priest.");
        pcService.updatePC(body, ownerId);

        assertThat(vestige.getStatBlock()).isEqualTo(before);
        verify(followerRepository, never()).save(any(PcFollower.class));
        verify(followerRepository, never()).deleteAll(anyList());
    }

    @Test
    void aSave_createsTheVestige_forAPatronWhoseTypeIsAlreadyStored() {
        // Stored choices, no follower yet (a character from before the companion existed).
        stored("Warlock", "Vestige Patron", 6, 14, null, "{\"vestigeType\":\"Undead\",\"vestigeDomain\":\"Life\"}");

        pcService.updatePC(sheet("Warlock", "Vestige Patron", 6, 14), ownerId);

        Map<String, Object> block = block(vestige());
        assertThat(block.get("race")).isEqualTo("Undead");
        assertThat(block.get("hp")).isEqualTo(Map.of("max", 28, "cur", 28, "temp", 0));
    }

    @Test
    void theDmsSave_thatChangesTheSubclass_removesTheVestige() throws Exception {
        stored("Warlock", "Vestige Patron", 5, 16, 7L, FIEND_WAR);
        existingVestige("Fiend", 5, 16, 24);

        PC saved = pcService.updatePCAsDm(sheet("Warlock", "Fiend Patron", 5, 16), null, dmId);

        assertThat(saved.getSubclassChoices()).isNull();
        assertThat(table).isEmpty();
    }

    @Test
    void theDmsSave_thatDropsTheLevelBelowThree_removesTheVestige() throws Exception {
        stored("Warlock", "Vestige Patron", 3, 16, 7L, FIEND_WAR);
        existingVestige("Fiend", 3, 16, 16);

        pcService.updatePCAsDm(sheet("Warlock", "Vestige Patron", 2, 16), null, dmId);

        assertThat(table).isEmpty();
    }

    @Test
    void theOwnersSave_thatChangesTheClass_removesTheVestige() throws Exception {
        stored("Warlock", "Vestige Patron", 5, 16, null, FIEND_WAR);
        existingVestige("Fiend", 5, 16, 24);

        pcService.updatePC(sheet("Wizard", "Evoker", 5, 16), ownerId);

        assertThat(table).isEmpty();
    }

    @Test
    void theDmsSave_withAProficiencyBonusLine_reachesTheVestige() throws Exception {
        stored("Warlock", "Vestige Patron", 5, 16, 7L, FIEND_WAR);
        existingVestige("Fiend", 5, 16, 24);

        PC body = sheet("Warlock", "Vestige Patron", 5, 16);
        body.setStatAdjustments("{\"prof\":[{\"value\":1,\"label\":\"Blessing\"}]}");
        pcService.updatePCAsDm(body, null, dmId);

        assertThat(block(vestige()).get("prof")).isEqualTo(4);
    }

    // ── the subclass-choices endpoint ────────────────────────────────────────────

    @Test
    void settingTheType_onTheSheet_createsTheVestige_forASubclassTheDmSetByHand() {
        // Warlock 7, subclass set by hand: no type, no domain, no companion.
        stored("Warlock", "Vestige Patron", 7, 18, 7L, null);

        pcService.setSubclassChoices(1L, Map.of("vestigeType", "Celestial"), ownerId);

        Map<String, Object> block = block(vestige());
        assertThat(block.get("race")).isEqualTo("Celestial");
        assertThat(block.get("hp")).isEqualTo(Map.of("max", 32, "cur", 32, "temp", 0));
        assertThat(block.get("ac")).isEqualTo(17);
    }

    @Test
    void settingOnlyTheDomain_createsNothing() {
        stored("Warlock", "Vestige Patron", 7, 18, 7L, null);

        pcService.setSubclassChoices(1L, Map.of("vestigeDomain", "Life"), ownerId);

        assertThat(table).isEmpty();
    }

    @Test
    void changingTheType_byTheOwnerOrTheDm_keepsTheNameAndTheHitPoints() throws Exception {
        stored("Warlock", "Vestige Patron", 5, 16, 7L, FIEND_WAR);
        existingVestige("Fiend", 5, 16, 9);

        pcService.setSubclassChoices(1L, Map.of("vestigeType", "Undead"), ownerId);
        assertThat(block(vestige()).get("race")).isEqualTo("Undead");
        assertThat(block(vestige()).get("hp")).isEqualTo(Map.of("max", 24, "cur", 9, "temp", 0));

        pcService.setSubclassChoices(1L, Map.of("vestigeType", "Celestial"), dmId);
        assertThat(block(vestige()).get("race")).isEqualTo("Celestial");
        assertThat(vestige().getName()).isEqualTo("Ashmaw");
        assertThat(vestige().getId()).isEqualTo(50L);
    }

    // ── nobody else pays for it ──────────────────────────────────────────────────

    @Test
    void anOrdinaryCharactersWrites_neverTouchTheFollowerTable() {
        PC fighter = stored("Fighter", "Champion", 8, 10, 7L, null);
        fighter.setPendingLevelGrant(true);
        pcService.updatePC(sheet("Fighter", "Champion", 8, 12), ownerId);
        pcService.updatePCAsDm(sheet("Fighter", "Champion", 8, 14), null, dmId);
        pcService.levelUpPC(1L, ownerId, null);
        pcService.levelUpPCAsDm(1L, dmId, null);

        // Another patron's warlock, at the levels a vestige would appear and grow.
        PC fiend = stored("Warlock", "Fiend Patron", 2, 16, 7L, null);
        pcService.levelUpPCAsDm(1L, dmId, null);
        assertThat(fiend.getLevel()).isEqualTo((short) 3);
        pcService.updatePC(sheet("Warlock", "Fiend Patron", 3, 18), ownerId);
        pcService.updatePCAsDm(sheet("Warlock", "Fiend Patron", 3, 20), null, dmId);

        // A subclass with choices of its own.
        stored("Fighter", "Arcane Archer", 5, 10, null, null);
        pcService.setSubclassChoices(1L,
                Map.of("arcaneShots", List.of("Banishing Shot", "Seeking Shot")), ownerId);

        verifyNoInteractions(followerRepository);
    }

    @Test
    void aCharacterBecomingAVestigePatronBelowLevelThree_isLookedAt_andGetsNothing() {
        stored("Warlock", "Fiend Patron", 2, 16, 7L, null);

        pcService.updatePCAsDm(sheet("Warlock", "Vestige Patron", 2, 16), null, dmId);

        assertThat(table).isEmpty();
        verify(followerRepository, never()).save(any(PcFollower.class));
    }

    // ── the live session ─────────────────────────────────────────────────────────

    private CombatSession liveSession() {
        CombatSession session = new CombatSession();
        session.setId(3L);
        lenient().when(sessionRepository.findByCampaignIdAndStatusNot(7L, SessionStatus.ENDED))
                .thenReturn(Optional.of(session));
        lenient().when(participantRepository.findBySessionIdAndPcId(3L, 1L))
                .thenReturn(Optional.of(new SessionParticipant()));
        return session;
    }

    @Test
    void aSaveThatChangesTheVestige_wakesTheLiveSession_andOneThatDoesNot_leavesItAsleep() throws Exception {
        stored("Warlock", "Vestige Patron", 5, 16, 7L, FIEND_WAR);
        existingVestige("Fiend", 5, 16, 24);
        CombatSession session = liveSession();

        // Same Charisma, same Hit Points: nothing for the table to see.
        pcService.updatePCAsDm(sheet("Warlock", "Vestige Patron", 5, 16), null, dmId);
        verify(sessionRepository, never()).save(any(CombatSession.class));

        // Charisma 18: the vestige's Armor Class changed under the owner's row.
        stored("Warlock", "Vestige Patron", 5, 16, 7L, FIEND_WAR);
        pcService.updatePCAsDm(sheet("Warlock", "Vestige Patron", 5, 18), null, dmId);
        verify(sessionRepository, times(1)).save(session);
    }
}
