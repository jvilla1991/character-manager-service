package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.HpMode;
import com.moo.charactermanagerservice.dto.LevelUpRequest;
import com.moo.charactermanagerservice.exceptions.PCNotFoundException;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.models.SessionParticipant;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.repositories.CampaignRepository;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.PcNoteRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The stored subclass choices on the PC write paths: who may set them through the
 * subclass-choices endpoint, what a whole-sheet save does with them (keeps the stored value,
 * never the body's), and that a level-up hands them to the rules engine.
 */
@ExtendWith(MockitoExtension.class)
class PCServiceSubclassChoicesTest {

    @Mock
    private PCRepository pcRepository;
    @Mock
    private LevelUpService levelUpService;
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
    private CombatLogService combatLogService;
    // A Vestige Patron's saves reach the companion step; what it does is VestigeCompanionSyncTest's.
    @Mock
    private VestigeCompanionSync vestigeCompanionSync;

    @InjectMocks
    private PCService pcService;

    private UUID ownerId;
    private UUID strangerId;
    private UUID dmId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        strangerId = UUID.randomUUID();
        dmId = UUID.randomUUID();
    }

    private PC stored(String clazz, String subclass, int level, Long campaignId, String choices) {
        PC pc = new PC();
        pc.setId(1L);
        pc.setName("Aelindra");
        pc.setClazz(clazz);
        pc.setSubclass(subclass);
        pc.setLevel((short) level);
        pc.setUserId(ownerId);
        pc.setCampaignId(campaignId);
        pc.setSubclassChoices(choices);
        lenient().when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        lenient().when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
        if (campaignId != null) {
            Campaign campaign = new Campaign();
            campaign.setId(campaignId);
            campaign.setDmUserId(dmId);
            lenient().when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        }
        return pc;
    }

    private static final String TWO_SHOTS = "{\"skills\":[],\"arcaneShots\":[\"Banishing Shot\",\"Seeking Shot\"]}";

    private static void assertStatus(Runnable call, int status, String reason) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode().value()).isEqualTo(status);
                    assertThat(rse.getReason()).isEqualTo(reason);
                });
    }

    // ── PUT /{id}/subclass-choices: who may set what ─────────────────────────────

    @Test
    void theCampaignDm_maySetAnyKey_evenOneThatIsAlreadyChosen() {
        PC warlock = stored("Warlock", "Vestige Patron", 5, 7L,
                "{\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"Life\"}");

        PC saved = pcService.setSubclassChoices(1L, Map.of("vestigeDomain", "War", "vestigeType", "Fiend"), dmId);

        assertThat(saved).isSameAs(warlock);
        assertThat(saved.getSubclassChoices()).isEqualTo("{\"vestigeType\":\"Fiend\",\"vestigeDomain\":\"War\"}");
        verify(pcRepository).save(warlock);
        verify(activityLogService).log(1L, PcActivityType.DM_EDIT,
                "DM set Vestige type: Fiend; Vestige domain: War", dmId);
    }

    @Test
    void theCampaignDm_mayReplaceASkillChoice_andTheProficiencyMovesWithIt() {
        PC cleric = stored("Cleric", "Arcana Domain", 6, 7L, "{\"skills\":[\"Insight\"]}");
        cleric.setSkills("{\"Insight\":\"prof\",\"Medicine\":\"prof\"}");

        pcService.setSubclassChoices(1L, Map.of("skills", List.of("Religion")), dmId);

        assertThat(cleric.getSubclassChoices()).isEqualTo("{\"skills\":[\"Religion\"]}");
        assertThat(cleric.getSkills()).isEqualTo("{\"Medicine\":\"prof\",\"Religion\":\"prof\"}");
        verify(activityLogService).log(1L, PcActivityType.DM_EDIT, "DM set subclass skills: Religion", dmId);
    }

    @Test
    void aReplacedSkillPick_keepsExpertise_whichDidNotComeFromThePick() {
        PC cleric = stored("Cleric", "Arcana Domain", 6, 7L, "{\"skills\":[\"Insight\"]}");
        cleric.setSkills("{\"Insight\":\"expert\"}");

        pcService.setSubclassChoices(1L, Map.of("skills", List.of("Arcana")), dmId);

        assertThat(cleric.getSkills()).isEqualTo("{\"Insight\":\"expert\",\"Arcana\":\"prof\"}");
    }

    @Test
    void theCampaignDm_maySwapAnyNumberOfArcaneShotOptions() {
        PC fighter = stored("Fighter", "Arcane Archer", 5, 7L, TWO_SHOTS);

        pcService.setSubclassChoices(1L, Map.of("arcaneShots", List.of("Shadow Shot", "Piercing Shot")), dmId);

        assertThat(fighter.getSubclassChoices())
                .isEqualTo("{\"skills\":[],\"arcaneShots\":[\"Shadow Shot\",\"Piercing Shot\"]}");
        verify(activityLogService).log(1L, PcActivityType.DM_EDIT,
                "DM set Arcane Shot options: Shadow Shot, Piercing Shot", dmId);
    }

    @Test
    void theOwnerOfACharacterInNoCampaign_maySetAnyKey() {
        PC warlock = stored("Warlock", "Vestige Patron", 5, null,
                "{\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"Life\"}");

        pcService.setSubclassChoices(1L, Map.of("vestigeDomain", "Trickery"), ownerId);

        assertThat(warlock.getSubclassChoices())
                .isEqualTo("{\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"Trickery\"}");
        verify(activityLogService).log(1L, PcActivityType.SUBCLASS_CHOICE, "Set Vestige domain: Trickery", ownerId);
        // No campaign: no session to wake.
        verifyNoInteractions(sessionRepository, participantRepository);
    }

    @Test
    void theOwnerInACampaign_mayAlwaysChangeTheVestigeType() {
        PC warlock = stored("Warlock", "Vestige Patron", 5, 7L,
                "{\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"Life\"}");

        pcService.setSubclassChoices(1L, Map.of("vestigeType", "Undead"), ownerId);

        assertThat(warlock.getSubclassChoices()).isEqualTo("{\"vestigeType\":\"Undead\",\"vestigeDomain\":\"Life\"}");
        verify(activityLogService).log(1L, PcActivityType.SUBCLASS_CHOICE, "Set Vestige type: Undead", ownerId);
    }

    @Test
    void theOwnerInACampaign_mayCatchUpAKeyThatIsMissing() {
        PC warlock = stored("Warlock", "Vestige Patron", 5, 7L, "{\"vestigeType\":\"Celestial\"}");
        pcService.setSubclassChoices(1L, Map.of("vestigeDomain", "Light"), ownerId);
        assertThat(warlock.getSubclassChoices()).isEqualTo("{\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"Light\"}");

        PC cleric = stored("Cleric", "Arcana Domain", 6, 7L, null);
        pcService.setSubclassChoices(1L, Map.of("skills", List.of("Arcana")), ownerId);
        assertThat(cleric.getSubclassChoices()).isEqualTo("{\"skills\":[\"Arcana\"]}");
        assertThat(cleric.getSkills()).isEqualTo("{\"Arcana\":\"prof\"}");

        // An Arcane Archer whose skills were never settled: Arcana and Nature arrive with the choice.
        PC fighter = stored("Fighter", "Arcane Archer", 5, 7L, null);
        fighter.setSkills("{\"Athletics\":\"prof\"}");
        pcService.setSubclassChoices(1L, Map.of("skills", List.of()), ownerId);
        assertThat(fighter.getSubclassChoices()).isEqualTo("{\"skills\":[]}");
        assertThat(fighter.getSkills()).isEqualTo("{\"Athletics\":\"prof\",\"Arcana\":\"prof\",\"Nature\":\"prof\"}");
        verify(activityLogService).log(1L, PcActivityType.SUBCLASS_CHOICE, "Set subclass skills: none", ownerId);
    }

    @Test
    void theOwnerInACampaign_mayFillAShortArcaneShotList_withoutSwapLimits() {
        // Nothing stored at level 5.
        PC fresh = stored("Fighter", "Arcane Archer", 5, 7L, "{\"skills\":[]}");
        pcService.setSubclassChoices(1L, Map.of("arcaneShots", List.of("Shadow Shot", "Seeking Shot")), ownerId);
        assertThat(fresh.getSubclassChoices())
                .isEqualTo("{\"skills\":[],\"arcaneShots\":[\"Shadow Shot\",\"Seeking Shot\"]}");

        // Two stored at level 7, where three are known: short, so the owner may set the list.
        PC short7 = stored("Fighter", "Arcane Archer", 7, 7L, TWO_SHOTS);
        pcService.setSubclassChoices(1L,
                Map.of("arcaneShots", List.of("Shadow Shot", "Piercing Shot", "Bursting Shot")), ownerId);
        assertThat(short7.getSubclassChoices()).isEqualTo(
                "{\"skills\":[],\"arcaneShots\":[\"Shadow Shot\",\"Piercing Shot\",\"Bursting Shot\"]}");
    }

    @Test
    void theOwnerInACampaign_cannotChangeAChoiceThatIsAlreadyMade() {
        PC warlock = stored("Warlock", "Vestige Patron", 5, 7L,
                "{\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"Life\"}");
        assertStatus(() -> pcService.setSubclassChoices(1L, Map.of("vestigeDomain", "War"), ownerId), 403,
                "In a campaign, only your Dungeon Master can change the Vestige domain once chosen");
        // One locked key refuses the whole request, the allowed key with it.
        assertStatus(() -> pcService.setSubclassChoices(1L,
                Map.of("vestigeType", "Fiend", "vestigeDomain", "War"), ownerId), 403,
                "In a campaign, only your Dungeon Master can change the Vestige domain once chosen");
        assertThat(warlock.getSubclassChoices()).isEqualTo("{\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"Life\"}");

        stored("Fighter", "Arcane Archer", 5, 7L, TWO_SHOTS);
        assertStatus(() -> pcService.setSubclassChoices(1L,
                Map.of("arcaneShots", List.of("Shadow Shot", "Piercing Shot")), ownerId), 403,
                "In a campaign, only your Dungeon Master can change the Arcane Shot options once chosen");
        // Settled skills too, an empty settled list included.
        assertStatus(() -> pcService.setSubclassChoices(1L, Map.of("skills", List.of()), ownerId), 403,
                "In a campaign, only your Dungeon Master can change the subclass skills once chosen");

        stored("Cleric", "Arcana Domain", 6, 7L, "{\"skills\":[\"Insight\"]}");
        assertStatus(() -> pcService.setSubclassChoices(1L, Map.of("skills", List.of("Religion")), ownerId), 403,
                "In a campaign, only your Dungeon Master can change the subclass skills once chosen");

        verify(pcRepository, never()).save(any());
        verifyNoInteractions(activityLogService);
    }

    @Test
    void anOwnerWhoRunsTheCampaign_hasTheDmsReach() {
        PC warlock = stored("Warlock", "Vestige Patron", 5, 7L,
                "{\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"Life\"}");
        Campaign own = new Campaign();
        own.setId(7L);
        own.setDmUserId(ownerId);
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(own));

        pcService.setSubclassChoices(1L, Map.of("vestigeDomain", "War"), ownerId);

        assertThat(warlock.getSubclassChoices()).isEqualTo("{\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"War\"}");
    }

    @Test
    void anyoneElse_isRefused_asOnTheOtherPcEndpoints() {
        // In a campaign the caller does not run.
        stored("Warlock", "Vestige Patron", 5, 7L, null);
        assertStatus(() -> pcService.setSubclassChoices(1L, Map.of("vestigeType", "Fiend"), strangerId), 403,
                "Access denied");
        // In no campaign: there is no DM to be.
        stored("Warlock", "Vestige Patron", 5, null, null);
        assertStatus(() -> pcService.setSubclassChoices(1L, Map.of("vestigeType", "Fiend"), strangerId), 403,
                "Character is not part of a campaign");
        assertStatus(() -> pcService.setSubclassChoices(1L, Map.of("vestigeType", "Fiend"), dmId), 403,
                "Character is not part of a campaign");
        verify(pcRepository, never()).save(any());

        when(pcRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> pcService.setSubclassChoices(99L, Map.of("vestigeType", "Fiend"), ownerId))
                .isInstanceOf(PCNotFoundException.class);
    }

    // ── PUT /{id}/subclass-choices: what is refused as a bad request ─────────────

    @Test
    void aBodyWithoutAKey_isABadRequest() {
        stored("Warlock", "Vestige Patron", 5, null, null);
        assertStatus(() -> pcService.setSubclassChoices(1L, null, ownerId), 400, "No subclass choice was sent");
        assertStatus(() -> pcService.setSubclassChoices(1L, Map.of(), ownerId), 400, "No subclass choice was sent");
        Map<String, Object> onlyNull = new java.util.HashMap<>();
        onlyNull.put("vestigeType", null);
        assertStatus(() -> pcService.setSubclassChoices(1L, onlyNull, ownerId), 400, "No subclass choice was sent");
        verify(pcRepository, never()).save(any());
    }

    @Test
    void aKeyTheCharacterDoesNotHave_orABadValue_isABadRequest() {
        stored("Wizard", "Evoker", 5, null, null);
        assertStatus(() -> pcService.setSubclassChoices(1L, Map.of("vestigeType", "Fiend"), ownerId), 400,
                "'vestigeType' is not a choice this character makes at level 5");
        assertStatus(() -> pcService.setSubclassChoices(1L, Map.of("maneuvers", List.of()), ownerId), 400,
                "'maneuvers' is not a subclass choice");

        stored("Warlock", "Vestige Patron", 2, null, null);
        assertStatus(() -> pcService.setSubclassChoices(1L, Map.of("vestigeType", "Fiend"), ownerId), 400,
                "'vestigeType' is not a choice this character makes at level 2");

        stored("Warlock", "Vestige Patron", 5, null, null);
        assertStatus(() -> pcService.setSubclassChoices(1L, Map.of("vestigeType", "Dragon"), ownerId), 400,
                "'Dragon' is not a Vestige type (Celestial, Fiend or Undead)");
        assertStatus(() -> pcService.setSubclassChoices(1L, Map.of("vestigeDomain", List.of("War")), ownerId), 400,
                "'vestigeDomain' must be a single name");

        stored("Fighter", "Arcane Archer", 5, null, null);
        assertStatus(() -> pcService.setSubclassChoices(1L, Map.of("arcaneShots", List.of("Seeking Shot")), ownerId),
                400, "An Arcane Archer knows exactly 2 Arcane Shot options at level 5");

        PC cleric = stored("Cleric", "Arcana Domain", 5, null, null);
        cleric.setSkills("{\"Insight\":\"prof\"}");
        assertStatus(() -> pcService.setSubclassChoices(1L, Map.of("skills", List.of("Insight")), ownerId), 400,
                "'Insight' is already a skill proficiency");
        assertStatus(() -> pcService.setSubclassChoices(1L, Map.of("skills", List.of()), ownerId), 400,
                "Arcana Domain requires exactly 1 skill choice");
        // An unknown or foreign key is a bad request before it is a question of who is asking.
        stored("Fighter", "Arcane Archer", 5, 7L, TWO_SHOTS);
        assertStatus(() -> pcService.setSubclassChoices(1L, Map.of("vestigeDomain", "War"), ownerId), 400,
                "'vestigeDomain' is not a choice this character makes at level 5");

        verify(pcRepository, never()).save(any());
        verifyNoInteractions(activityLogService);
    }

    // ── PUT /{id}/subclass-choices: the live session ─────────────────────────────

    @Test
    void settingAChoice_wakesTheLiveSessionTheCharacterSitsIn() {
        stored("Warlock", "Vestige Patron", 5, 7L, null);
        CombatSession session = new CombatSession();
        session.setId(40L);
        session.setCampaignId(7L);
        session.setDmUserId(dmId);
        session.setStatus(SessionStatus.ACTIVE);
        session.setVersion(3L);
        when(sessionRepository.findByCampaignIdAndStatusNot(7L, SessionStatus.ENDED)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdAndPcId(40L, 1L)).thenReturn(Optional.of(new SessionParticipant()));

        pcService.setSubclassChoices(1L, Map.of("vestigeType", "Fiend"), ownerId);

        assertThat(session.getVersion()).isEqualTo(4L);
        verify(sessionRepository).save(session);
    }

    // ── Whole-sheet saves: the stored choices are kept, the body's never read ────

    private static PC body(String clazz, String subclass, int level, String choices) {
        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz(clazz);
        incoming.setSubclass(subclass);
        incoming.setLevel((short) level);
        incoming.setSubclassChoices(choices);
        return incoming;
    }

    @Test
    void addPC_neverStartsWithSubclassChoices() {
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
        PC created = body("Warlock", "Vestige Patron", 1, "{\"vestigeType\":\"Fiend\"}");

        assertThat(pcService.addPC(created).getSubclassChoices()).isNull();
    }

    @Test
    void updatePC_keepsTheStoredChoices_whateverTheBodySays() {
        stored("Fighter", "Arcane Archer", 5, null, TWO_SHOTS);

        // The body omits them, echoes a stale copy, or tries to set its own: the stored value stays.
        assertThat(pcService.updatePC(body("Fighter", "Arcane Archer", 5, null), ownerId).getSubclassChoices())
                .isEqualTo(TWO_SHOTS);
        stored("Fighter", "Arcane Archer", 5, null, TWO_SHOTS);
        assertThat(pcService.updatePC(body("Fighter", "Arcane Archer", 5,
                "{\"skills\":[\"Perception\"],\"arcaneShots\":[\"Shadow Shot\",\"Piercing Shot\"]}"), ownerId)
                .getSubclassChoices()).isEqualTo(TWO_SHOTS);
        // A character with none stored gets none from a body.
        stored("Fighter", "Arcane Archer", 5, null, null);
        assertThat(pcService.updatePC(body("Fighter", "Arcane Archer", 5, TWO_SHOTS), ownerId)
                .getSubclassChoices()).isNull();
    }

    @Test
    void updatePC_dropsTheChoices_whenTheSubclassIsChanged() {
        stored("Fighter", "Arcane Archer", 5, null, TWO_SHOTS);
        assertThat(pcService.updatePC(body("Fighter", "Champion", 5, null), ownerId).getSubclassChoices()).isNull();

        stored("Fighter", "Arcane Archer", 5, null, TWO_SHOTS);
        assertThat(pcService.updatePC(body("Wizard", "Arcane Archer", 5, null), ownerId).getSubclassChoices())
                .isNull();

        stored("Cleric", "Arcana Domain", 5, null, "{\"skills\":[\"Insight\"]}");
        assertThat(pcService.updatePC(body("Cleric", null, 5, null), ownerId).getSubclassChoices()).isNull();
    }

    @Test
    void updatePC_fitsTheChoicesToTheLevelBeingSaved() {
        stored("Fighter", "Arcane Archer", 7, null,
                "{\"skills\":[],\"arcaneShots\":[\"Banishing Shot\",\"Seeking Shot\",\"Shadow Shot\"]}");
        assertThat(pcService.updatePC(body("Fighter", "Arcane Archer", 6, null), ownerId).getSubclassChoices())
                .isEqualTo(TWO_SHOTS);

        stored("Fighter", "Arcane Archer", 7, null, TWO_SHOTS);
        assertThat(pcService.updatePC(body("Fighter", "Arcane Archer", 2, null), ownerId).getSubclassChoices())
                .isNull();
    }

    @Test
    void updatePCAsDm_keepsTheStoredChoices_andDropsThemWithTheSubclass() {
        stored("Warlock", "Vestige Patron", 5, 7L, "{\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"Life\"}");
        assertThat(pcService.updatePCAsDm(body("Warlock", "Vestige Patron", 5, "{\"vestigeDomain\":\"War\"}"),
                null, dmId).getSubclassChoices())
                .isEqualTo("{\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"Life\"}");

        stored("Warlock", "Vestige Patron", 5, 7L, "{\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"Life\"}");
        assertThat(pcService.updatePCAsDm(body("Warlock", "Fiend Patron", 5, null), null, dmId)
                .getSubclassChoices()).isNull();
    }

    @Test
    void aSheetSaveOfACharacterWithoutChoices_storesNone() {
        stored("Cleric", "Life Domain", 5, null, null);
        assertThat(pcService.updatePC(body("Cleric", "Life Domain", 5, "{\"skills\":[\"Insight\"]}"), ownerId)
                .getSubclassChoices()).isNull();
    }

    // ── Level-up: the two new request fields reach the rules engine ──────────────

    @Test
    void levelUp_forwardsTheSubclassChoicesAndTheSpellsToForget() {
        PC cleric = stored("Cleric", "Arcana Domain", 5, null, "{\"skills\":[\"Insight\"]}");
        cleric.setPendingLevelGrant(true);
        Map<String, Object> choices = Map.of("skills", List.of("Insight"));
        List<String> forget = List.of("Mage Hand");
        List<Map<String, Object>> spells = List.of(Map.of("lvl", 0, "name", "Light", "grantedBy", "Student of Arcana"));

        pcService.levelUpPC(1L, ownerId,
                new LevelUpRequest(null, null, null, spells, null, null, null, choices, forget));

        verify(levelUpService).applyLevelUp(cleric, null, null, null, spells, HpMode.AVERAGE, null, null,
                choices, forget);
    }

    @Test
    void levelUpAsDm_forwardsThemToo_andAnOlderRequestSendsNone() {
        PC warlock = stored("Warlock", "Vestige Patron", 2, 7L, null);
        Map<String, Object> choices = Map.of("vestigeType", "Fiend");

        pcService.levelUpPCAsDm(1L, dmId,
                new LevelUpRequest(null, null, null, null, null, null, null, choices, null));
        verify(levelUpService).applyLevelUp(warlock, null, null, null, null, HpMode.AVERAGE, null, null,
                choices, null);

        pcService.levelUpPCAsDm(1L, dmId, new LevelUpRequest(null, null, null, null, HpMode.AVERAGE, null, null));
        verify(levelUpService).applyLevelUp(warlock, null, null, null, null, HpMode.AVERAGE, null, null,
                null, null);
    }
}
