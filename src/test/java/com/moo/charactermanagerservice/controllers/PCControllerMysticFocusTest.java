package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.MysticFocusRequest;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.services.PCService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** {@code POST /api/v1/pc/{id}/mystic-focus}: the controller hands the body's two fields to the service. */
@ExtendWith(MockitoExtension.class)
class PCControllerMysticFocusTest {

    @InjectMocks
    private PCController pcController;

    @Mock
    private PCService pcService;

    private UUID userId;
    private Authentication auth;
    private PC pc;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        User user = new User();
        user.setUuid(userId);
        user.setUserName("testuser");
        user.setFirstName("Test");
        auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

        pc = new PC();
        pc.setId(1L);
        pc.setClazz("Monk");
        pc.setSubclass("Warrior of the Mystic Arts");
    }

    @Test
    void mysticFocus_returns200_withTheSavedCharacter_andPassesTheActionAndSlotLevel() {
        when(pcService.mysticFocus(1L, "SLOT_TO_FOCUS", 2, userId)).thenReturn(pc);

        ResponseEntity<PC> response =
                pcController.mysticFocus(1L, new MysticFocusRequest("SLOT_TO_FOCUS", 2), auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(pc);
    }

    @Test
    void mysticFocus_withNoBody_leavesTheRefusalToTheService() {
        when(pcService.mysticFocus(1L, null, null, userId)).thenThrow(new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "action must be SLOT_TO_FOCUS or FOCUS_TO_SLOT"));

        assertThatThrownBy(() -> pcController.mysticFocus(1L, null, auth))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void mysticFocus_propagatesTheServicesRefusals() {
        when(pcService.mysticFocus(1L, "FOCUS_TO_SLOT", 1, userId)).thenThrow(new ResponseStatusException(
                HttpStatus.CONFLICT, "No spent level 1 slot to restore"));

        assertThatThrownBy(() -> pcController.mysticFocus(1L, new MysticFocusRequest("FOCUS_TO_SLOT", 1), auth))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }
}
