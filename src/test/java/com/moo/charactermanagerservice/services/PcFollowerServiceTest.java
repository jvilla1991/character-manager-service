package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.FollowerRequest;
import com.moo.charactermanagerservice.dto.FollowerView;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.models.PcFollower;
import com.moo.charactermanagerservice.repositories.PcFollowerRepository;
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

    private PcFollowerService followerService;

    private UUID ownerId;
    private UUID dmId;
    private UUID strangerId;
    private PC pc;

    @BeforeEach
    void setUp() {
        followerService = new PcFollowerService(pcService, followerRepository, activityLogService,
                new ObjectMapper());

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
    void dismiss_isDmOnly_evenTheOwnerIsRefused() {
        assertThatThrownBy(() -> followerService.dismiss(1L, 5L, ownerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
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
