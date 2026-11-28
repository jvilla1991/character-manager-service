package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.FollowerRequest;
import com.moo.charactermanagerservice.dto.FollowerView;
import com.moo.charactermanagerservice.models.FollowerKind;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.models.PcFollower;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.SessionCombatLog;
import com.moo.charactermanagerservice.repositories.PcFollowerRepository;
import com.moo.charactermanagerservice.repositories.SessionCombatLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PcFollowerServiceTest {

    @Mock
    private PCService pcService;

    @Mock
    private PcFollowerRepository followerRepository;

    @Mock
    private PcActivityLogService activityLogService;

    @Mock
    private SessionCombatLogRepository combatLogRepository;

    private PcFollowerService followerService;

    private UUID ownerId;
    private UUID dmId;
    private UUID strangerId;
    private PC pc;

    @BeforeEach
    void setUp() {
        followerService = new PcFollowerService(pcService, followerRepository, activityLogService,
                new CombatLogService(combatLogRepository, new ObjectMapper()), new ObjectMapper());

        ownerId = UUID.randomUUID();
        dmId = UUID.randomUUID();
        strangerId = UUID.randomUUID();

        pc = new PC();
        pc.setId(1L);
        pc.setUserId(ownerId);
        pc.setCampaignId(7L);

        // PCService owns the authorization rules (covered by PCServiceTest); here
        // it is stubbed to: the DM passes the campaign-DM check, a stranger gets 403.
        lenient().when(pcService.findPCById(1L)).thenReturn(pc);
        lenient().when(pcService.findPCByIdForDm(1L, dmId)).thenReturn(pc);
        lenient().when(pcService.findPCByIdForDm(1L, ownerId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));
        lenient().when(pcService.findPCByIdForDm(1L, strangerId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));
    }

    private static Map<String, Object> statBlock() {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("clazz", "Guard");
        block.put("ac", 16);
        block.put("hp", Map.of("cur", 11, "max", 11, "temp", 0));
        return block;
    }

    private PcFollower stored(long id, long pcId, String name) {
        PcFollower follower = new PcFollower();
        follower.setId(id);
        follower.setPcId(pcId);
        follower.setName(name);
        follower.setStatBlock("{\"clazz\":\"Guard\",\"ac\":16}");
        return follower;
    }

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    // --- grant ---

    @Test
    void grant_byCampaignDm_savesTrimmedNameAndSerializedBlock_andLogs() {
        when(followerRepository.countByPcId(1L)).thenReturn(0L);
        when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> inv.getArgument(0));

        FollowerView view = followerService.grant(1L, new FollowerRequest("  Brannoc  ", statBlock()), dmId);

        ArgumentCaptor<PcFollower> saved = ArgumentCaptor.forClass(PcFollower.class);
        verify(followerRepository).save(saved.capture());
        assertThat(saved.getValue().getPcId()).isEqualTo(1L);
        assertThat(saved.getValue().getName()).isEqualTo("Brannoc");
        assertThat(saved.getValue().getGrantedByUserId()).isEqualTo(dmId);
        assertThat(saved.getValue().getStatBlock()).contains("\"clazz\":\"Guard\"");

        // The view hands the block back as a parsed object, not a JSON string.
        assertThat(view.name()).isEqualTo("Brannoc");
        assertThat(view.statBlock()).containsEntry("ac", 16);
        verify(activityLogService).log(eq(1L), eq(PcActivityType.DM_EDIT), contains("Brannoc"), eq(dmId));
    }

    @Test
    void grant_isDmOnly_evenTheOwnerIsRefused() {
        assertThatThrownBy(() -> followerService.grant(1L, new FollowerRequest("Brannoc", statBlock()), ownerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        assertThatThrownBy(() -> followerService.grant(1L, new FollowerRequest("Brannoc", statBlock()), strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        verify(followerRepository, never()).save(any());
    }

    @Test
    void grant_rejectsBlankName_missingBlock_andOverlongName() {
        assertThatThrownBy(() -> followerService.grant(1L, new FollowerRequest("   ", statBlock()), dmId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThatThrownBy(() -> followerService.grant(1L, new FollowerRequest("Brannoc", null), dmId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThatThrownBy(() -> followerService.grant(1L,
                new FollowerRequest("x".repeat(PcFollowerService.MAX_NAME_LENGTH + 1), statBlock()), dmId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(followerRepository, never()).save(any());
    }

    @Test
    void grant_rejectsAnOversizedStatBlock() {
        Map<String, Object> huge = Map.of("bio", "x".repeat(PcFollowerService.MAX_STAT_BLOCK_CHARS));

        assertThatThrownBy(() -> followerService.grant(1L, new FollowerRequest("Brannoc", huge), dmId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(followerRepository, never()).save(any());
    }

    @Test
    void grant_refusesPastTheFollowerCap() {
        when(followerRepository.countByPcId(1L)).thenReturn((long) PcFollowerService.MAX_FOLLOWERS_PER_PC);

        assertThatThrownBy(() -> followerService.grant(1L, new FollowerRequest("Brannoc", statBlock()), dmId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(followerRepository, never()).save(any());
    }

    // --- read ---

    @Test
    void followersFor_readableByOwnerAndCampaignDm_deniedToStrangers() {
        when(followerRepository.findByPcIdOrderByCreatedAtAscIdAsc(1L))
                .thenReturn(List.of(stored(5L, 1L, "Brannoc")));

        assertThat(followerService.followersFor(1L, ownerId)).extracting(FollowerView::name)
                .containsExactly("Brannoc");
        assertThat(followerService.followersFor(1L, dmId)).hasSize(1);

        assertThatThrownBy(() -> followerService.followersFor(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    // --- update ---

    @Test
    void update_byOwner_replacesTheBlock_andKeepsTheNameWhenOmitted() {
        PcFollower follower = stored(5L, 1L, "Brannoc");
        when(followerRepository.findById(5L)).thenReturn(Optional.of(follower));
        when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> inv.getArgument(0));

        FollowerView view = followerService.update(1L, 5L,
                new FollowerRequest(null, Map.of("ac", 18)), ownerId);

        assertThat(view.name()).isEqualTo("Brannoc");
        assertThat(view.statBlock()).containsEntry("ac", 18).doesNotContainKey("clazz");
    }

    @Test
    void update_changingHpMidSession_isLoggedToThatSessionsCombatLog() {
        pc.setName("Aelindra");
        PcFollower follower = stored(5L, 1L, "Brannoc");
        follower.setStatBlock("{\"hp\":{\"cur\":9,\"max\":16,\"temp\":0},\"conditions\":[]}");
        when(followerRepository.findById(5L)).thenReturn(Optional.of(follower));
        when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> inv.getArgument(0));
        CombatSession session = new CombatSession();
        session.setId(3L);
        when(pcService.liveSessionFor(pc)).thenReturn(Optional.of(session));

        followerService.update(1L, 5L, new FollowerRequest(null,
                Map.of("hp", Map.of("cur", 4, "max", 16, "temp", 0), "conditions", List.of("Prone"))), ownerId);

        ArgumentCaptor<SessionCombatLog> row = ArgumentCaptor.forClass(SessionCombatLog.class);
        verify(combatLogRepository).save(row.capture());
        assertThat(row.getValue().getSessionId()).isEqualTo(3L);
        assertThat(row.getValue().getSubjectName()).isEqualTo("Brannoc (Aelindra's follower)");
        assertThat(row.getValue().getDescription())
                .isEqualTo("HP 9 → 4. Condition added: Prone. Changed on the sheet by the player.");
    }

    @Test
    void update_thatLeavesHpAndConditionsAlone_logsNothing_andLooksUpNoSession() {
        PcFollower follower = stored(5L, 1L, "Brannoc");
        when(followerRepository.findById(5L)).thenReturn(Optional.of(follower));
        when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> inv.getArgument(0));

        followerService.update(1L, 5L, new FollowerRequest(null, Map.of("ac", 18)), ownerId);

        verify(pcService, never()).liveSessionFor(any());
        verify(combatLogRepository, never()).save(any());
    }

    @Test
    void update_byCampaignDm_canRename() {
        PcFollower follower = stored(5L, 1L, "Brannoc");
        when(followerRepository.findById(5L)).thenReturn(Optional.of(follower));
        when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> inv.getArgument(0));

        FollowerView view = followerService.update(1L, 5L, new FollowerRequest(" Sir Brannoc ", null), dmId);

        assertThat(view.name()).isEqualTo("Sir Brannoc");
        assertThat(view.statBlock()).containsEntry("clazz", "Guard"); // block untouched
    }

    @Test
    void update_deniedToStrangers_and400WhenNothingToChange() {
        assertThatThrownBy(() -> followerService.update(1L, 5L,
                new FollowerRequest(null, Map.of("ac", 18)), strangerId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));

        when(followerRepository.findById(5L)).thenReturn(Optional.of(stored(5L, 1L, "Brannoc")));
        assertThatThrownBy(() -> followerService.update(1L, 5L, new FollowerRequest(null, null), ownerId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(followerRepository, never()).save(any());
    }

    @Test
    void update_404sForAFollowerOfAnotherCharacter() {
        // Follower 9 belongs to PC 2 — reaching it through PC 1's URL must not work.
        when(followerRepository.findById(9L)).thenReturn(Optional.of(stored(9L, 2L, "Someone else's")));

        assertThatThrownBy(() -> followerService.update(1L, 9L,
                new FollowerRequest(null, Map.of("ac", 18)), ownerId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
        verify(followerRepository, never()).save(any());
    }

    // --- dismiss ---

    @Test
    void dismiss_byCampaignDm_deletesAndLogs() {
        PcFollower follower = stored(5L, 1L, "Brannoc");
        when(followerRepository.findById(5L)).thenReturn(Optional.of(follower));

        followerService.dismiss(1L, 5L, dmId);

        verify(followerRepository).delete(follower);
        verify(activityLogService).log(eq(1L), eq(PcActivityType.DM_EDIT), contains("Brannoc"), eq(dmId));
    }

    @Test
    void dismiss_ofADmGrant_isDmOnly_evenTheOwnerIsRefused() {
        // The follower's kind decides, so the row is read first: a grant is the DM's.
        when(followerRepository.findById(5L)).thenReturn(Optional.of(stored(5L, 1L, "Brannoc")));

        assertThatThrownBy(() -> followerService.dismiss(1L, 5L, ownerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        verify(followerRepository, never()).delete(any());
    }

    // --- kinds (V64): summons and familiars are the player's own ---

    private PcFollower stored(long id, String name, FollowerKind kind) {
        PcFollower follower = stored(id, 1L, name);
        follower.setKind(kind);
        return follower;
    }

    private static FollowerRequest request(String name, String kind) {
        return new FollowerRequest(name, statBlock(), kind);
    }

    @Test
    void aRequestBodyWithoutAKind_stillReads_andOneWithAKindCarriesIt() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        FollowerRequest old = mapper.readValue("{\"name\":\"Brannoc\",\"statBlock\":{\"ac\":16}}",
                FollowerRequest.class);
        assertThat(old.kind()).isNull();
        assertThat(old.statBlock()).containsEntry("ac", 16);

        FollowerRequest withKind = mapper.readValue(
                "{\"name\":\"Thorn\",\"statBlock\":{},\"kind\":\"SUMMON\"}", FollowerRequest.class);
        assertThat(withKind.kind()).isEqualTo("SUMMON");
    }

    @Test
    void summon_byTheOwner_ofACharacterInNoCampaign_isSaved_andLoggedAsThePlayersOwn() {
        pc.setCampaignId(null);
        when(followerRepository.countByPcId(1L)).thenReturn(0L);
        when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> inv.getArgument(0));

        FollowerView view = followerService.grant(1L, request(" Thorn ", "SUMMON"), ownerId);

        ArgumentCaptor<PcFollower> saved = ArgumentCaptor.forClass(PcFollower.class);
        verify(followerRepository).save(saved.capture());
        assertThat(saved.getValue().getKind()).isEqualTo(FollowerKind.SUMMON);
        assertThat(saved.getValue().getName()).isEqualTo("Thorn");
        assertThat(saved.getValue().getGrantedByUserId()).isEqualTo(ownerId);
        assertThat(view.kind()).isEqualTo("SUMMON");
        verify(activityLogService).log(1L, PcActivityType.FOLLOWER, "Summoned a follower: Thorn", ownerId);
        verify(activityLogService, never()).log(any(), eq(PcActivityType.DM_EDIT), any(), any());
        verify(pcService).bumpLiveSessionVersion(pc);
    }

    @Test
    void summonAndFamiliar_byTheOwner_ofACampaignCharacter_areAllowed() {
        when(followerRepository.countByPcId(1L)).thenReturn(0L);
        when(followerRepository.findByPcIdAndKind(1L, FollowerKind.FAMILIAR)).thenReturn(List.of());
        when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(followerService.grant(1L, request("Thorn", "SUMMON"), ownerId).kind()).isEqualTo("SUMMON");
        assertThat(followerService.grant(1L, request("Pip", "FAMILIAR"), ownerId).kind()).isEqualTo("FAMILIAR");

        verify(activityLogService).log(1L, PcActivityType.FOLLOWER, "Summoned a follower: Thorn", ownerId);
        verify(activityLogService).log(1L, PcActivityType.FOLLOWER, "Summoned a familiar: Pip", ownerId);
        verify(pcService, times(2)).bumpLiveSessionVersion(pc);
    }

    @Test
    void summonAndFamiliar_byTheCampaignDm_areAllowed_andLoggedAsDmEdits() {
        when(followerRepository.countByPcId(1L)).thenReturn(0L);
        when(followerRepository.findByPcIdAndKind(1L, FollowerKind.FAMILIAR)).thenReturn(List.of());
        when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(followerService.grant(1L, request("Thorn", "SUMMON"), dmId).kind()).isEqualTo("SUMMON");
        assertThat(followerService.grant(1L, request("Pip", "FAMILIAR"), dmId).kind()).isEqualTo("FAMILIAR");

        verify(activityLogService).log(1L, PcActivityType.DM_EDIT, "DM summoned a follower: Thorn", dmId);
        verify(activityLogService).log(1L, PcActivityType.DM_EDIT, "DM summoned a familiar: Pip", dmId);
        verify(activityLogService, never()).log(any(), eq(PcActivityType.FOLLOWER), any(), any());
    }

    @Test
    void summonAndFamiliar_areRefusedToStrangers() {
        for (String kind : List.of("SUMMON", "FAMILIAR")) {
            assertThatThrownBy(() -> followerService.grant(1L, request("Thorn", kind), strangerId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        }
        verify(followerRepository, never()).save(any());
        verify(followerRepository, never()).deleteAll(any());
        verify(pcService, never()).bumpLiveSessionVersion(any());
    }

    @Test
    void aNewFamiliar_replacesTheCurrentOne_andTheLogNamesBoth() {
        PcFollower old = stored(4L, "Old Pip", FollowerKind.FAMILIAR);
        when(followerRepository.findByPcIdAndKind(1L, FollowerKind.FAMILIAR)).thenReturn(List.of(old));
        when(followerRepository.countByPcId(1L)).thenReturn(3L);
        when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> inv.getArgument(0));

        FollowerView view = followerService.grant(1L, request("Pip", "FAMILIAR"), ownerId);

        verify(followerRepository).deleteAll(List.of(old));
        assertThat(view.kind()).isEqualTo("FAMILIAR");
        assertThat(view.name()).isEqualTo("Pip");
        verify(activityLogService).log(1L, PcActivityType.FOLLOWER,
                "Summoned a familiar: Pip (replaces Old Pip)", ownerId);
    }

    @Test
    void aSummon_leavesTheFamiliarAlone() {
        when(followerRepository.countByPcId(1L)).thenReturn(1L);
        when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> inv.getArgument(0));

        followerService.grant(1L, request("Thorn", "SUMMON"), ownerId);

        verify(followerRepository, never()).findByPcIdAndKind(any(), any());
        verify(followerRepository, never()).deleteAll(any());
    }

    @Test
    void theCap_appliesToEveryKind_butAReplacedFamiliarDoesNotCount() {
        when(followerRepository.countByPcId(1L)).thenReturn((long) PcFollowerService.MAX_FOLLOWERS_PER_PC);

        // Full, and nothing to replace: a summon and a first familiar are both refused.
        when(followerRepository.findByPcIdAndKind(1L, FollowerKind.FAMILIAR)).thenReturn(List.of());
        assertThatThrownBy(() -> followerService.grant(1L, request("Thorn", "SUMMON"), ownerId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        assertThatThrownBy(() -> followerService.grant(1L, request("Pip", "FAMILIAR"), ownerId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(followerRepository, never()).save(any());
        verify(followerRepository, never()).deleteAll(any());

        // Full, but one of the twenty is the familiar being replaced: allowed.
        PcFollower old = stored(4L, "Old Pip", FollowerKind.FAMILIAR);
        when(followerRepository.findByPcIdAndKind(1L, FollowerKind.FAMILIAR)).thenReturn(List.of(old));
        when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(followerService.grant(1L, request("Pip", "FAMILIAR"), ownerId).kind()).isEqualTo("FAMILIAR");
        verify(followerRepository).deleteAll(List.of(old));
    }

    @Test
    void grant_rejectsCompanionAndUnknownKinds_withAReadableMessage() {
        for (String kind : List.of("COMPANION", "PET", "summon")) {
            for (UUID caller : List.of(ownerId, dmId)) {
                assertThatThrownBy(() -> followerService.grant(1L, request("Thorn", kind), caller))
                        .isInstanceOf(ResponseStatusException.class)
                        .satisfies(e -> {
                            assertThat(status(e)).isEqualTo(400);
                            assertThat(((ResponseStatusException) e).getReason())
                                    .isEqualTo("Follower kind must be DM_GRANT, SUMMON or FAMILIAR");
                        });
            }
        }
        verify(followerRepository, never()).save(any());
        verify(pcService, never()).bumpLiveSessionVersion(any());
    }

    @Test
    void dmGrant_namedOrLeftOut_keepsItsRules_dmOnly_andTheViewSaysDmGrant() {
        when(followerRepository.countByPcId(1L)).thenReturn(0L);
        when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(followerService.grant(1L, request("Brannoc", "DM_GRANT"), dmId).kind()).isEqualTo("DM_GRANT");
        assertThat(followerService.grant(1L, request("Brannoc", null), dmId).kind()).isEqualTo("DM_GRANT");
        assertThat(followerService.grant(1L, request("Brannoc", "  "), dmId).kind()).isEqualTo("DM_GRANT");
        verify(activityLogService, times(3))
                .log(1L, PcActivityType.DM_EDIT, "DM granted a follower: Brannoc", dmId);
        verify(followerRepository, never()).findByPcIdAndKind(any(), any());

        for (UUID caller : List.of(ownerId, strangerId)) {
            assertThatThrownBy(() -> followerService.grant(1L, request("Brannoc", "DM_GRANT"), caller))
                    .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        }
        verify(followerRepository, times(3)).save(any());
    }

    @Test
    void followersFor_carriesEachFollowersKind() {
        when(followerRepository.findByPcIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(
                stored(5L, 1L, "Brannoc"),
                stored(6L, "Thorn", FollowerKind.SUMMON),
                stored(7L, "Pip", FollowerKind.FAMILIAR),
                stored(8L, "Fang", FollowerKind.COMPANION)));

        assertThat(followerService.followersFor(1L, ownerId)).extracting(FollowerView::kind)
                .containsExactly("DM_GRANT", "SUMMON", "FAMILIAR", "COMPANION");
    }

    @Test
    void update_neverChangesTheKind_whateverTheBodySays() {
        PcFollower follower = stored(6L, "Thorn", FollowerKind.SUMMON);
        when(followerRepository.findById(6L)).thenReturn(Optional.of(follower));
        when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> inv.getArgument(0));

        FollowerView view = followerService.update(1L, 6L,
                new FollowerRequest("Thorn", Map.of("ac", 18), "FAMILIAR"), ownerId);

        assertThat(view.kind()).isEqualTo("SUMMON");
        assertThat(follower.getKind()).isEqualTo(FollowerKind.SUMMON);
    }

    @Test
    void dismiss_byTheOwner_removesTheirOwnSummonOrFamiliar_andLogsItAsThePlayersOwn() {
        PcFollower summon = stored(6L, "Thorn", FollowerKind.SUMMON);
        PcFollower familiar = stored(7L, "Pip", FollowerKind.FAMILIAR);
        when(followerRepository.findById(6L)).thenReturn(Optional.of(summon));
        when(followerRepository.findById(7L)).thenReturn(Optional.of(familiar));

        followerService.dismiss(1L, 6L, ownerId);
        followerService.dismiss(1L, 7L, ownerId);

        verify(followerRepository).delete(summon);
        verify(followerRepository).delete(familiar);
        verify(activityLogService).log(1L, PcActivityType.FOLLOWER, "Dismissed the follower Thorn", ownerId);
        verify(activityLogService).log(1L, PcActivityType.FOLLOWER, "Dismissed the familiar Pip", ownerId);
        verify(activityLogService, never()).log(any(), eq(PcActivityType.DM_EDIT), any(), any());
        verify(pcService, times(2)).bumpLiveSessionVersion(pc);
    }

    @Test
    void dismiss_byTheOwner_worksForACharacterInNoCampaign() {
        pc.setCampaignId(null);
        PcFollower familiar = stored(7L, "Pip", FollowerKind.FAMILIAR);
        when(followerRepository.findById(7L)).thenReturn(Optional.of(familiar));

        followerService.dismiss(1L, 7L, ownerId);

        verify(followerRepository).delete(familiar);
    }

    @Test
    void dismiss_byTheOwner_isRefusedForADmGrantAndACompanion() {
        when(followerRepository.findById(5L)).thenReturn(Optional.of(stored(5L, "Brannoc", FollowerKind.DM_GRANT)));
        when(followerRepository.findById(8L)).thenReturn(Optional.of(stored(8L, "Fang", FollowerKind.COMPANION)));

        for (long id : List.of(5L, 8L)) {
            assertThatThrownBy(() -> followerService.dismiss(1L, id, ownerId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        }
        verify(followerRepository, never()).delete(any());
        verify(pcService, never()).bumpLiveSessionVersion(any());
    }

    @Test
    void dismiss_byTheCampaignDm_removesAGrantASummonAndAFamiliar_asADmEdit() {
        List<PcFollower> all = List.of(
                stored(5L, "Brannoc", FollowerKind.DM_GRANT),
                stored(6L, "Thorn", FollowerKind.SUMMON),
                stored(7L, "Pip", FollowerKind.FAMILIAR));
        for (PcFollower follower : all) {
            when(followerRepository.findById(follower.getId())).thenReturn(Optional.of(follower));
            followerService.dismiss(1L, follower.getId(), dmId);
            verify(followerRepository).delete(follower);
        }

        verify(activityLogService).log(1L, PcActivityType.DM_EDIT, "DM dismissed the follower Brannoc", dmId);
        verify(activityLogService).log(1L, PcActivityType.DM_EDIT, "DM dismissed the follower Thorn", dmId);
        verify(activityLogService).log(1L, PcActivityType.DM_EDIT, "DM dismissed the familiar Pip", dmId);
        verify(activityLogService, never()).log(any(), eq(PcActivityType.FOLLOWER), any(), any());
    }

    // --- companions: the server's own ---

    private static final String VESTIGE_BLOCK = "{\"companion\":\"vestige\",\"clazz\":\"Vestige\","
            + "\"hp\":{\"max\":24,\"cur\":24,\"temp\":0},\"ac\":17,\"conditions\":[],"
            + "\"conditionImmunities\":[\"charmed\",\"frightened\",\"prone\"],"
            + "\"features\":[{\"name\":\"Vestige's Strike\",\"source\":\"Action\",\"desc\":\"+7 to hit\"}]}";

    private PcFollower companion() {
        PcFollower follower = stored(8L, "Vestige", FollowerKind.COMPANION);
        follower.setStatBlock(VESTIGE_BLOCK);
        lenient().when(followerRepository.findById(8L)).thenReturn(Optional.of(follower));
        lenient().when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> inv.getArgument(0));
        return follower;
    }

    private static Map<String, Object> hp(int cur, int max, int temp) {
        Map<String, Object> hp = new LinkedHashMap<>();
        hp.put("cur", cur);
        hp.put("max", max);
        hp.put("temp", temp);
        return hp;
    }

    @Test
    void dismiss_ofACompanion_isRefusedToEveryone_theDmIncluded() {
        PcFollower follower = companion();

        for (UUID caller : List.of(ownerId, dmId)) {
            assertThatThrownBy(() -> followerService.dismiss(1L, 8L, caller))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> {
                        assertThat(status(e)).isEqualTo(403);
                        assertThat(((ResponseStatusException) e).getReason()).isEqualTo("A companion cannot be "
                                + "dismissed. It stays for as long as its character has the feature that gives it");
                    });
        }
        // Not even for a character in no campaign, where the owner stands in for the DM elsewhere.
        pc.setCampaignId(null);
        assertThatThrownBy(() -> followerService.dismiss(1L, 8L, ownerId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));

        verify(followerRepository, never()).delete(any());
        verify(activityLogService, never()).log(any(), any(), any(), any());
        verify(pcService, never()).bumpLiveSessionVersion(any());
        assertThat(follower.getStatBlock()).isEqualTo(VESTIGE_BLOCK);
    }

    @Test
    void update_ofACompanion_takesOnlyHitPointsTemporaryHitPointsAndConditions() {
        for (UUID caller : List.of(ownerId, dmId)) {
            PcFollower follower = companion();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("companion", "pet");
            body.put("clazz", "Dragon");
            body.put("ac", 30);
            body.put("hp", hp(9, 999, 5));
            body.put("conditions", List.of("poisoned"));
            body.put("conditionImmunities", List.of());
            body.put("features", List.of(Map.of("name", "Breath Weapon", "source", "Action", "desc", "9d6")));
            body.put("spells", List.of(Map.of("name", "Wish", "lvl", 9)));

            FollowerView view = followerService.update(1L, 8L, new FollowerRequest(null, body), caller);

            Map<String, Object> expected = readJson(VESTIGE_BLOCK);
            expected.put("hp", Map.of("max", 24, "cur", 9, "temp", 5));
            expected.put("conditions", List.of("poisoned"));
            assertThat(view.statBlock()).isEqualTo(expected);
            assertThat(view.statBlock()).doesNotContainKey("spells");
            assertThat(view.kind()).isEqualTo("COMPANION");
            assertThat(view.name()).isEqualTo("Vestige");
        }
        verify(pcService, times(2)).bumpLiveSessionVersion(pc);
    }

    private static Map<String, Object> readJson(String json) {
        try {
            return new ObjectMapper().readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void update_ofACompanion_keepsHitPointsWithinTheStoredMaximum_andDropsWhatItIsImmuneTo() {
        PcFollower follower = companion();

        // Above the maximum, a negative temporary value, an immune condition, a duplicate, junk.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hp", hp(60, 60, -4));
        body.put("conditions", java.util.Arrays.asList("Prone", "poisoned", "Poisoned", " ", 7, "blinded"));
        Map<String, Object> saved = followerService.update(1L, 8L, new FollowerRequest(null, body), ownerId)
                .statBlock();
        assertThat(saved.get("hp")).isEqualTo(Map.of("max", 24, "cur", 24, "temp", 0));
        assertThat(saved.get("conditions")).isEqualTo(List.of("poisoned", "blinded"));

        // Below zero stops at zero.
        saved = followerService.update(1L, 8L, new FollowerRequest(null, Map.of("hp", hp(-3, 24, 0))), dmId)
                .statBlock();
        assertThat(((Map<?, ?>) saved.get("hp")).get("cur")).isEqualTo(0);
        assertThat(follower.getKind()).isEqualTo(FollowerKind.COMPANION);
    }

    @Test
    void update_ofACompanion_keepsWhatTheBodyLeavesOut() {
        PcFollower follower = companion();
        follower.setStatBlock(VESTIGE_BLOCK.replace("\"cur\":24,\"temp\":0", "\"cur\":11,\"temp\":3")
                .replace("\"conditions\":[]", "\"conditions\":[\"stunned\"]"));

        // A body with nothing the server takes: the block is saved as it was.
        Map<String, Object> saved = followerService.update(1L, 8L,
                new FollowerRequest(null, Map.of("ac", 2, "hp", "full")), ownerId).statBlock();
        assertThat(saved.get("hp")).isEqualTo(Map.of("max", 24, "cur", 11, "temp", 3));
        assertThat(saved.get("conditions")).isEqualTo(List.of("stunned"));
        assertThat(saved.get("ac")).isEqualTo(17);

        // Only the conditions sent: the Hit Points stay.
        saved = followerService.update(1L, 8L,
                new FollowerRequest(null, Map.of("conditions", List.of())), ownerId).statBlock();
        assertThat(saved.get("hp")).isEqualTo(Map.of("max", 24, "cur", 11, "temp", 3));
        assertThat(saved.get("conditions")).isEqualTo(List.of());
    }

    @Test
    void update_ofACompanion_canRenameIt_andANameAloneLeavesTheBlockAlone() {
        PcFollower follower = companion();

        FollowerView view = followerService.update(1L, 8L, new FollowerRequest(" Ashmaw ", null), ownerId);

        assertThat(view.name()).isEqualTo("Ashmaw");
        assertThat(follower.getStatBlock()).isEqualTo(VESTIGE_BLOCK);
        assertThatThrownBy(() -> followerService.update(1L, 8L, new FollowerRequest(" ", null), ownerId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void update_ofACompanion_logsAHitPointChangeMidSession_likeAnyFollower() {
        pc.setName("Aelindra");
        companion();
        CombatSession session = new CombatSession();
        session.setId(3L);
        when(pcService.liveSessionFor(pc)).thenReturn(Optional.of(session));

        followerService.update(1L, 8L, new FollowerRequest(null, Map.of("hp", hp(20, 24, 0))), ownerId);

        ArgumentCaptor<SessionCombatLog> row = ArgumentCaptor.forClass(SessionCombatLog.class);
        verify(combatLogRepository).save(row.capture());
        assertThat(row.getValue().getSubjectName()).isEqualTo("Vestige (Aelindra's follower)");
        assertThat(row.getValue().getDescription()).isEqualTo("HP 24 → 20. Changed on the sheet by the player.");
    }

    @Test
    void update_ofACompanion_isStillRefusedToStrangers() {
        companion();
        assertThatThrownBy(() -> followerService.update(1L, 8L,
                new FollowerRequest(null, Map.of("hp", hp(1, 24, 0))), strangerId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        verify(followerRepository, never()).save(any());
    }

    @Test
    void dismiss_isRefusedToStrangers_whateverTheKind() {
        for (long id : List.of(5L, 6L, 7L, 8L)) {
            assertThatThrownBy(() -> followerService.dismiss(1L, id, strangerId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        }
        // Refused before the follower is even looked up.
        verify(followerRepository, never()).findById(any());
        verify(followerRepository, never()).delete(any());
    }

    @Test
    void dismiss_404sForASummonOfAnotherCharacter() {
        PcFollower elsewhere = stored(9L, 2L, "Someone else's");
        elsewhere.setKind(FollowerKind.SUMMON);
        when(followerRepository.findById(9L)).thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() -> followerService.dismiss(1L, 9L, ownerId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
        verify(followerRepository, never()).delete(any());
    }

    // --- waking the live session ---

    @Test
    void grant_update_andDismiss_eachWakeTheOwnersLiveSession() {
        // A follower rides in its owner's session row, so each write must bump
        // that session's version or the 2s poll would keep answering 204.
        when(followerRepository.countByPcId(1L)).thenReturn(0L);
        when(followerRepository.save(any(PcFollower.class))).thenAnswer(inv -> inv.getArgument(0));
        PcFollower follower = stored(5L, 1L, "Brannoc");
        when(followerRepository.findById(5L)).thenReturn(Optional.of(follower));

        followerService.grant(1L, new FollowerRequest("Brannoc", statBlock()), dmId);
        verify(pcService, times(1)).bumpLiveSessionVersion(pc);

        followerService.update(1L, 5L, new FollowerRequest(null, Map.of("ac", 18)), ownerId);
        verify(pcService, times(2)).bumpLiveSessionVersion(pc);

        followerService.dismiss(1L, 5L, dmId);
        verify(pcService, times(3)).bumpLiveSessionVersion(pc);
    }

    @Test
    void aRefusedWrite_neverWakesTheSession() {
        when(followerRepository.findById(5L)).thenReturn(Optional.of(stored(5L, 1L, "Brannoc")));

        assertThatThrownBy(() -> followerService.update(1L, 5L, new FollowerRequest(null, null), ownerId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThatThrownBy(() -> followerService.dismiss(1L, 5L, ownerId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        assertThatThrownBy(() -> followerService.grant(1L, new FollowerRequest(" ", statBlock()), dmId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));

        verify(pcService, never()).bumpLiveSessionVersion(any());
    }
}
