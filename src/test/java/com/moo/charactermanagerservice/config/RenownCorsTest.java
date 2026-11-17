package com.moo.charactermanagerservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.DefaultCorsProcessor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RenownCorsTest {
    @Test
    void browserCanPreflightRenownAdjustmentFromConfiguredOrigin() throws Exception {
        SecurityConfig security = new SecurityConfig(mock(AuthorizationFilter.class));
        ReflectionTestUtils.setField(security, "corsAllowedOrigins", "https://table-mimic.villa-dev.com");
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/pc/1/renown/1");
        request.addHeader("Origin", "https://table-mimic.villa-dev.com");
        request.addHeader("Access-Control-Request-Method", "PATCH");
        request.addHeader("Access-Control-Request-Headers", "authorization,content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(new DefaultCorsProcessor().processRequest(
                security.corsConfigurationSource().getCorsConfiguration(request), request, response));
        assertEquals(200, response.getStatus());
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("PATCH"));
        assertEquals("https://table-mimic.villa-dev.com", response.getHeader("Access-Control-Allow-Origin"));
    }
}
