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
