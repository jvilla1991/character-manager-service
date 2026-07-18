package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.SessionNoteView;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.SessionNote;
import com.moo.charactermanagerservice.repositories.SessionNoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionNoteServiceTest {

    @Mock
    private SessionNoteRepository sessionNoteRepository;
    @Mock
    private CampaignService campaignService;

    @InjectMocks
    private SessionNoteService sessionNoteService;

    private UUID dmId;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        dmId = UUID.randomUUID();
        campaign = new Campaign();
        campaign.setId(1L);
        campaign.setDmUserId(dmId);
    }

    // --- addNote ---

    @Test
    void addNote_savesAndReturns_whenOwner() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(campaign);
        when(sessionNoteRepository.save(any(SessionNote.class))).thenAnswer(inv -> {
            SessionNote n = inv.getArgument(0);
            n.setId(10L);
            return n;
        });

        SessionNoteView view = sessionNoteService.addNote(1L, "  The cult struck back  ", 5L, dmId);

        assertThat(view.id()).isEqualTo(10L);
        assertThat(view.campaignId()).isEqualTo(1L);
        assertThat(view.sessionId()).isEqualTo(5L);
        assertThat(view.body()).isEqualTo("The cult struck back");   // trimmed
        verify(sessionNoteRepository).save(any(SessionNote.class));
    }

    @Test
    void addNote_allowsNullSessionId_fromCampaignMenu() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(campaign);
        when(sessionNoteRepository.save(any(SessionNote.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionNoteView view = sessionNoteService.addNote(1L, "Out-of-session prep", null, dmId);

        assertThat(view.sessionId()).isNull();
    }

    @Test
    void addNote_throws400_whenBodyBlank() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(campaign);

        assertThatThrownBy(() -> sessionNoteService.addNote(1L, "   ", null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
        verify(sessionNoteRepository, never()).save(any());
    }

    @Test
    void addNote_propagates403_whenNotOwner() {
        when(campaignService.findByIdForDm(1L, dmId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        assertThatThrownBy(() -> sessionNoteService.addNote(1L, "anything", null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
        verify(sessionNoteRepository, never()).save(any());
    }

    // --- listNotes ---

    @Test
    void listNotes_returnsMappedViews_whenOwner() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(campaign);
        when(sessionNoteRepository.findByCampaignIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(note(2L, "newer"), note(1L, "older")));

        List<SessionNoteView> views = sessionNoteService.listNotes(1L, dmId);

        assertThat(views).extracting(SessionNoteView::body).containsExactly("newer", "older");
    }

    // --- updateNote ---

    @Test
    void updateNote_savesTrimmedBody_whenOwnerAndNoteOnCampaign() {
        SessionNote existing = note(7L, "old text");
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(campaign);
        when(sessionNoteRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(sessionNoteRepository.save(any(SessionNote.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionNoteView view = sessionNoteService.updateNote(1L, 7L, "  The cult regrouped  ", dmId);

        assertThat(view.body()).isEqualTo("The cult regrouped"); // trimmed
        verify(sessionNoteRepository).save(existing);
    }

    @Test
    void updateNote_throws400_whenBodyBlank() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(campaign);

        assertThatThrownBy(() -> sessionNoteService.updateNote(1L, 7L, "   ", dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
        verify(sessionNoteRepository, never()).save(any());
    }

    @Test
    void updateNote_throws404_whenNoteMissingOrOnDifferentCampaign() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(campaign);
        when(sessionNoteRepository.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> sessionNoteService.updateNote(1L, 7L, "text", dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));

        SessionNote other = note(8L, "belongs elsewhere");
        other.setCampaignId(99L);
        when(sessionNoteRepository.findById(8L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> sessionNoteService.updateNote(1L, 8L, "text", dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
        verify(sessionNoteRepository, never()).save(any());
    }

    @Test
    void updateNote_propagates403_whenNotOwner() {
        when(campaignService.findByIdForDm(1L, dmId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        assertThatThrownBy(() -> sessionNoteService.updateNote(1L, 7L, "text", dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
        verify(sessionNoteRepository, never()).save(any());
    }

    // --- deleteNote ---

    @Test
    void deleteNote_deletes_whenOwnerAndNoteOnCampaign() {
        SessionNote existing = note(7L, "to remove");
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(campaign);
        when(sessionNoteRepository.findById(7L)).thenReturn(Optional.of(existing));

        sessionNoteService.deleteNote(1L, 7L, dmId);

        verify(sessionNoteRepository).delete(existing);
    }

    @Test
    void deleteNote_throws404_whenNoteMissing() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(campaign);
        when(sessionNoteRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionNoteService.deleteNote(1L, 7L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
        verify(sessionNoteRepository, never()).delete(any());
    }

    @Test
    void deleteNote_throws404_whenNoteOnDifferentCampaign() {
        SessionNote other = note(7L, "belongs elsewhere");
        other.setCampaignId(99L);
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(campaign);
        when(sessionNoteRepository.findById(7L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> sessionNoteService.deleteNote(1L, 7L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
        verify(sessionNoteRepository, never()).delete(any());
    }

    @Test
    void deleteNote_propagates403_whenNotOwner() {
        when(campaignService.findByIdForDm(1L, dmId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        assertThatThrownBy(() -> sessionNoteService.deleteNote(1L, 7L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
        verify(sessionNoteRepository, never()).delete(any());
    }

    private SessionNote note(Long id, String body) {
        SessionNote n = new SessionNote();
        n.setId(id);
        n.setCampaignId(1L);
        n.setDmUserId(dmId);
        n.setBody(body);
        return n;
    }
}
