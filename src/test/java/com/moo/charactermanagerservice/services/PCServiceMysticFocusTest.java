package com.moo.charactermanagerservice.services;

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
 * Mystic Focus on the PC write path ({@code POST /{id}/mystic-focus}): who may use it, what it
 * saves and logs, that a refusal changes nothing, and that the live session hears of it. The
 * rules themselves are {@link MysticFocusRulesTest}'s.
 */
@ExtendWith(MockitoExtension.class)
class PCServiceMysticFocusTest {

    private static final String MYSTIC_ARTS = "Warrior of the Mystic Arts";
    private static final String SLOT_TO_FOCUS = "SLOT_TO_FOCUS";
    private static final String FOCUS_TO_SLOT = "FOCUS_TO_SLOT";

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

    private PC character(String clazz, String subclass, int level, Long campaignId) {
        PC pc = new PC();
        pc.setId(1L);
        pc.setName("Shen");
        pc.setClazz(clazz);
        pc.setSubclass(subclass);
        pc.setLevel((short) level);
        pc.setUserId(ownerId);
        pc.setCampaignId(campaignId);
        return pc;
    }

    /** A Monk 8 of the Mystic Arts (slots 4 / 2, Focus Points 8) stored behind the row lock. */
    private PC lockedMonk(Long campaignId, String slots, String resources) {
        PC pc = character("Monk", MYSTIC_ARTS, 8, campaignId);
        pc.setSpellSlots(slots);
        pc.setClassResources(resources);
        lenient().when(pcRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pc));
        lenient().when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
        if (campaignId != null) {
            Campaign campaign = new Campaign();
            campaign.setId(campaignId);
            campaign.setDmUserId(dmId);
            lenient().when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        }
        return pc;
    }

    private static final String FRESH = "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":0}}";
    private static final String ONE_OF_EACH_SPENT = "{\"1\":{\"max\":4,\"used\":1},\"2\":{\"max\":2,\"used\":1}}";

    private static void assertStatus(Runnable call, int status, String reason) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode().value()).isEqualTo(status);
                    assertThat(rse.getReason()).isEqualTo(reason);
                });
    }

    // ── POST /{id}/mystic-focus: who may ─────────────────────────────────────────

    @Test
    void theOwner_inNoCampaign_turnsASlotIntoFocusPoints_savesAndLogsIt() {
        PC monk = lockedMonk(null, FRESH, "{\"focusPoints\":5}");

        PC saved = pcService.mysticFocus(1L, SLOT_TO_FOCUS, 2, ownerId);

        assertThat(saved).isSameAs(monk);
        assertThat(monk.getSpellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":1}}");
        assertThat(monk.getClassResources()).isEqualTo("{\"focusPoints\":3}");
        verify(pcRepository).save(monk);
        verify(activityLogService).log(1L, PcActivityType.SPELL_SLOT,
                "Mystic Focus: turned a level 2 spell slot into 2 Focus Points "
                        + "(1 of 2 slots left, 5 of 8 Focus Points)", ownerId);
    }

    @Test
    void theOwner_inACampaign_isAllowed_bothWays() {
        // Unlike spending a slot by hand, this is the player's own feature: a campaign does not close it.
        PC monk = lockedMonk(7L, ONE_OF_EACH_SPENT, "{\"focusPoints\":2}");

        pcService.mysticFocus(1L, SLOT_TO_FOCUS, 1, ownerId);
        assertThat(monk.getSpellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":2},\"2\":{\"max\":2,\"used\":1}}");
        assertThat(monk.getClassResources()).isEqualTo("{\"focusPoints\":1}");

        pcService.mysticFocus(1L, FOCUS_TO_SLOT, 2, ownerId);
        assertThat(monk.getSpellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":2},\"2\":{\"max\":2,\"used\":0}}");
        assertThat(monk.getClassResources()).isEqualTo("{\"focusPoints\":4}");
        verify(activityLogService).log(1L, PcActivityType.SPELL_SLOT,
                "Mystic Focus: turned 3 Focus Points into a level 2 spell slot "
                        + "(2 of 2 slots left, 4 of 8 Focus Points)", ownerId);
    }

    @Test
    void theCampaignDm_isAllowed_andTheLogSaysSo() {
        PC monk = lockedMonk(7L, ONE_OF_EACH_SPENT, null);

        pcService.mysticFocus(1L, FOCUS_TO_SLOT, 1, dmId);

        assertThat(monk.getSpellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":1}}");
        assertThat(monk.getClassResources()).isEqualTo("{\"focusPoints\":2}");
        verify(activityLogService).log(1L, PcActivityType.SPELL_SLOT,
                "DM used Mystic Focus: turned 2 Focus Points into a level 1 spell slot "
                        + "(4 of 4 slots left, 6 of 8 Focus Points)", dmId);
    }

    @Test
    void aStranger_isRefused_403_inACampaignOrNot() {
        PC inCampaign = lockedMonk(7L, FRESH, "{\"focusPoints\":5}");
        assertStatus(() -> pcService.mysticFocus(1L, SLOT_TO_FOCUS, 1, strangerId), 403, "Access denied");
        assertThat(inCampaign.getSpellSlots()).isEqualTo(FRESH);

        lockedMonk(null, FRESH, "{\"focusPoints\":5}");
        assertStatus(() -> pcService.mysticFocus(1L, SLOT_TO_FOCUS, 1, strangerId), 403,
                "Character is not part of a campaign");

        verify(pcRepository, never()).save(any());
        verifyNoInteractions(activityLogService);
    }

    @Test
    void theDmOfAnotherCampaign_isAStranger() {
        lockedMonk(7L, FRESH, "{\"focusPoints\":5}");
        Campaign other = new Campaign();
        other.setId(7L);
        other.setDmUserId(UUID.randomUUID());
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(other));

        assertStatus(() -> pcService.mysticFocus(1L, SLOT_TO_FOCUS, 1, dmId), 403, "Access denied");
    }

    @Test
    void anUnknownCharacter_is404() {
        when(pcRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pcService.mysticFocus(99L, SLOT_TO_FOCUS, 1, ownerId))
                .isInstanceOf(PCNotFoundException.class);
    }

    @Test
    void theRowIsLocked_likeASpend() {
        lockedMonk(null, FRESH, "{\"focusPoints\":5}");

        pcService.mysticFocus(1L, SLOT_TO_FOCUS, 1, ownerId);

        verify(pcRepository).findByIdForUpdate(1L);
        verify(pcRepository, never()).findById(any());
    }

    // ── Refusals leave everything as it was ──────────────────────────────────────

    @Test
    void aRefusedConversion_savesNothing_andLogsNothing() {
        PC monk = lockedMonk(null, FRESH, null);

        // 409: nothing expended to regain; nothing spent to recover.
        assertStatus(() -> pcService.mysticFocus(1L, SLOT_TO_FOCUS, 1, ownerId), 409,
                "No expended Focus Points to regain");
        assertStatus(() -> pcService.mysticFocus(1L, FOCUS_TO_SLOT, 1, ownerId), 409,
                "No spent level 1 slot to restore");
        // 400: a slot level a Monk 8 cannot recover; a bad action; a bad slot level.
        assertStatus(() -> pcService.mysticFocus(1L, FOCUS_TO_SLOT, 3, ownerId), 400,
                "Recovering a level 3 spell slot needs Monk level 13");
        assertStatus(() -> pcService.mysticFocus(1L, "BOTH", 1, ownerId), 400,
                "action must be SLOT_TO_FOCUS or FOCUS_TO_SLOT");
        assertStatus(() -> pcService.mysticFocus(1L, SLOT_TO_FOCUS, null, ownerId), 400,
                "slotLevel must be a spell level from 1 to 4");

        assertThat(monk.getSpellSlots()).isEqualTo(FRESH);
        assertThat(monk.getClassResources()).isNull();
        verify(pcRepository, never()).save(any());
        verifyNoInteractions(activityLogService);
        verifyNoInteractions(sessionRepository);
    }

    @Test
    void aCharacterWithoutTheFeature_isRefused_400_evenForItsOwnerAndItsDm() {
        String refused = "Mystic Focus needs a Warrior of the Mystic Arts of Monk level 6 or higher";
        PC openHand = lockedMonk(7L, null, "{\"focusPoints\":3}");
        openHand.setSubclass("Warrior of the Open Hand");
        assertStatus(() -> pcService.mysticFocus(1L, SLOT_TO_FOCUS, 1, ownerId), 400, refused);
        assertStatus(() -> pcService.mysticFocus(1L, FOCUS_TO_SLOT, 1, dmId), 400, refused);

        PC young = lockedMonk(null, "{\"1\":{\"max\":3,\"used\":1}}", "{\"focusPoints\":3}");
        young.setLevel((short) 5);
        assertStatus(() -> pcService.mysticFocus(1L, SLOT_TO_FOCUS, 1, ownerId), 400, refused);

        assertThat(openHand.getClassResources()).isEqualTo("{\"focusPoints\":3}");
        verify(pcRepository, never()).save(any());
    }

    // ── The live session sees it ─────────────────────────────────────────────────

    @Test
    void aConversion_wakesTheCharactersLiveSession() {
        lockedMonk(7L, FRESH, "{\"focusPoints\":5}");
        CombatSession session = new CombatSession();
        session.setId(40L);
        session.setCampaignId(7L);
        session.setDmUserId(dmId);
        session.setStatus(SessionStatus.ACTIVE);
        session.setVersion(3L);
        when(sessionRepository.findByCampaignIdAndStatusNot(7L, SessionStatus.ENDED)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdAndPcId(40L, 1L)).thenReturn(Optional.of(new SessionParticipant()));

        pcService.mysticFocus(1L, SLOT_TO_FOCUS, 1, ownerId);

        assertThat(session.getVersion()).isEqualTo(4L);
        verify(sessionRepository).save(session);
    }
}
