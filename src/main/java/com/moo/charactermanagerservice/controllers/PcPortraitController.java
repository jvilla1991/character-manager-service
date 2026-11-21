package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.PortraitVersionView;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.services.PcPortraitService;
import com.moo.charactermanagerservice.services.PcPortraitService.PortraitImage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;

/**
 * A character's portrait image. The upload body is the raw image bytes (not multipart), typed
 * by the request's {@code Content-Type}. Clients fetch the image as
 * {@code GET /api/v1/pc/{id}/portrait?v=<portraitVersion>}: the version changes on every
 * upload, so the response may be cached for good.
 */
@RestController
@RequestMapping("/api/v1/pc/{id}/portrait")
public class PcPortraitController {

    static final String CACHE_CONTROL = "private, max-age=31536000, immutable";

    private final PcPortraitService service;

    public PcPortraitController(PcPortraitService service) {
        this.service = service;
    }

    /**
     * Upload or replace the portrait — the owner or the campaign's DM. 200
     * {@code {"portraitVersion": n}}; 415 for a type other than JPEG/PNG/WebP, 413 over 2 MB,
     * 400 for an empty body or bytes that are not the declared type.
     *
     * <p>The body is read straight off the request, at most one byte past the limit, so an
     * oversized upload is never buffered whole.
     */
    @PutMapping
    public ResponseEntity<PortraitVersionView> upload(@PathVariable Long id,
                                                      @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                      HttpServletRequest request,
                                                      Authentication authentication) throws IOException {
        if (request.getContentLengthLong() > PcPortraitService.MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Portrait must be at most 2 MB");
        }
        byte[] image;
        try (InputStream body = request.getInputStream()) {
            image = body.readNBytes(PcPortraitService.MAX_BYTES + 1);
        }
        User user = (User) authentication.getPrincipal();
        long version = service.upload(id, contentType, image, user.getUuid());
        return ResponseEntity.ok(new PortraitVersionView(version));
    }

    /** The portrait bytes with their stored type — owner, campaign DM or a fellow campaign player. 404 when none. */
    @GetMapping
    public ResponseEntity<byte[]> get(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        PortraitImage portrait = service.get(id, user.getUuid());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(portrait.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .body(portrait.image());
    }

    /** Remove the portrait — the owner or the campaign's DM. 204, also when there was none. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        service.delete(id, user.getUuid());
        return ResponseEntity.noContent().build();
    }
}
