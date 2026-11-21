package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.PortraitVersionView;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.services.PcPortraitService;
import com.moo.charactermanagerservice.services.PcPortraitService.PortraitImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for PcPortraitController — controller logic only, like {@link PCControllerTest}. */
@ExtendWith(MockitoExtension.class)
class PcPortraitControllerTest {

    @InjectMocks
    private PcPortraitController controller;

    @Mock
    private PcPortraitService service;

    private UUID userId;
    private Authentication auth;

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        User user = new User();
        user.setUuid(userId);
        user.setUserName("testuser");
        auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private static MockHttpServletRequest put(String type, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/pc/1/portrait");
        request.setContentType(type);
        request.setContent(body);
        return request;
    }

    @Test
    void upload_passesTheRawBytesAndType_andReturnsTheVersion() throws Exception {
        when(service.upload(1L, "image/png", PNG, userId)).thenReturn(1234L);

        ResponseEntity<PortraitVersionView> response =
                controller.upload(1L, "image/png", put("image/png", PNG), auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new PortraitVersionView(1234L));
    }

    @Test
    void upload_rejectsADeclaredLengthOver2Mb_withoutReadingTheBody() {
        MockHttpServletRequest request = put("image/png", new byte[PcPortraitService.MAX_BYTES + 1]);

        assertThatThrownBy(() -> controller.upload(1L, "image/png", request, auth))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(413));
        verifyNoInteractions(service);
    }

    @Test
    void upload_readsAtMostOneBytePastTheLimit_whenNoLengthIsDeclared() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/pc/1/portrait") {
            @Override public long getContentLengthLong() { return -1; } // chunked upload
        };
        request.setContent(new byte[PcPortraitService.MAX_BYTES + 500]);
        when(service.upload(eq(1L), eq("image/png"), any(), eq(userId)))
                .thenThrow(new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE));

        assertThatThrownBy(() -> controller.upload(1L, "image/png", request, auth))
                .isInstanceOf(ResponseStatusException.class);
        verify(service).upload(eq(1L), eq("image/png"),
                argThat(bytes -> bytes.length == PcPortraitService.MAX_BYTES + 1), eq(userId));
    }

    @Test
    void get_returnsTheBytesWithTheirTypeAndAnImmutableCacheHeader() {
        when(service.get(1L, userId)).thenReturn(new PortraitImage("image/png", PNG));

        ResponseEntity<byte[]> response = controller.get(1L, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("private, max-age=31536000, immutable");
        assertThat(response.getBody()).isEqualTo(PNG);
    }

    @Test
    void get_propagatesTheServices404() {
        when(service.get(1L, userId)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> controller.get(1L, auth)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void delete_returns204() {
        ResponseEntity<Void> response = controller.delete(1L, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(1L, userId);
    }
}
