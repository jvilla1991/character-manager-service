package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.exceptions.PCNotFoundException;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcPortrait;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.PcPortraitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Character portraits (V62): one uploaded image per character, stored in {@code pc_portrait},
 * with {@link PC#getPortraitVersion()} as the cache key clients put on the image URL.
 *
 * <p>Writing (upload, delete) is for the character's owner and the DM of its campaign. Reading
 * is for those two and for the other players of the same campaign, who see the party's faces
 * on the campaign and session screens. Everyone else gets the 403 the rest of the PC API uses;
 * an unknown character is 404.
 */
@Service
public class PcPortraitService {

    /** Largest accepted image, in bytes (2 MB). */
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF = {0x52, 0x49, 0x46, 0x46}; // "RIFF"
    private static final byte[] WEBP = {0x57, 0x45, 0x42, 0x50}; // "WEBP", at offset 8

    /** A stored portrait as served: its content type and bytes. */
    public record PortraitImage(String contentType, byte[] image) {}

    private final PCService pcs;
    private final PCRepository pcRepository;
    private final PcPortraitRepository portraits;

    public PcPortraitService(PCService pcs, PCRepository pcRepository, PcPortraitRepository portraits) {
        this.pcs = pcs;
        this.pcRepository = pcRepository;
        this.portraits = portraits;
    }

    /**
     * Store (or replace) a character's portrait and return its new version. Checks, in order:
     * the character exists (404), the caller may write (403), the declared type is JPEG/PNG/WebP
     * (415), the body is no larger than {@link #MAX_BYTES} (413), and it is a non-empty image
     * whose leading bytes match the declared type (400). The new version always differs from
     * the old one — the current epoch milliseconds, or the previous version + 1 if that is not
     * larger — so a client never shows an image it cached for an earlier upload, even across a
     * delete and re-upload.
     */
    @Transactional
    public long upload(Long pcId, String contentType, byte[] image, UUID userId) {
        authorizeWrite(pcs.findPCById(pcId), userId);
        String type = requireAllowedType(contentType);
        if (image != null && image.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Portrait must be at most 2 MB");
        }
        if (image == null || image.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Portrait image is empty");
        }
        if (!matchesMagic(type, image)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Portrait bytes are not a valid " + type + " image");
        }
        // Row lock: two uploads at once must still each get a distinct, increasing version.
        PC pc = lock(pcId);
        PcPortrait portrait = portraits.findById(pcId).orElseGet(PcPortrait::new);
        portrait.setPcId(pcId);
        portrait.setContentType(type);
        portrait.setImage(image);
        portrait.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        portraits.save(portrait);

        Long previous = pc.getPortraitVersion();
        long next = Math.max(System.currentTimeMillis(), previous == null ? 1L : previous + 1);
        pc.setPortraitVersion(next);
        pcRepository.save(pc);
        return next;
    }

    /** The stored portrait. 404 when the character has none. */
    @Transactional(readOnly = true)
    public PortraitImage get(Long pcId, UUID userId) {
        authorizeRead(pcs.findPCById(pcId), userId);
        PcPortrait portrait = portraits.findById(pcId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Character has no portrait"));
        return new PortraitImage(portrait.getContentType(), portrait.getImage());
    }

    /** Remove the portrait and clear the version. Removing a portrait that is not there is a no-op. */
    @Transactional
    public void delete(Long pcId, UUID userId) {
        authorizeWrite(pcs.findPCById(pcId), userId);
        PC pc = lock(pcId);
        portraits.findById(pcId).ifPresent(portraits::delete);
        if (pc.getPortraitVersion() != null) {
            pc.setPortraitVersion(null);
            pcRepository.save(pc);
        }
    }

    // --- authorization -------------------------------------------------------

    private void authorizeWrite(PC pc, UUID userId) {
        if (!userId.equals(pc.getUserId())) {
            pcs.findPCByIdForDm(pc.getId(), userId); // not the owner → must be the campaign's DM (403 otherwise)
        }
    }

    private void authorizeRead(PC pc, UUID userId) {
        if (userId.equals(pc.getUserId())) return;
        if (pc.getCampaignId() != null
                && pcRepository.existsByCampaignIdAndUserId(pc.getCampaignId(), userId)) {
            return; // a fellow player in the same campaign
        }
        pcs.findPCByIdForDm(pc.getId(), userId); // otherwise only the campaign's DM (403 otherwise)
    }

    // --- validation ----------------------------------------------------------

    /** The declared type as "type/subtype" (parameters dropped), or 415 when it is not allowed. */
    static String requireAllowedType(String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            try {
                MediaType parsed = MediaType.parseMediaType(contentType);
                String type = (parsed.getType() + "/" + parsed.getSubtype()).toLowerCase(Locale.ROOT);
                if (ALLOWED_TYPES.contains(type)) return type;
            } catch (InvalidMediaTypeException ignored) {
                // falls through to 415
            }
        }
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Portrait must be image/jpeg, image/png or image/webp");
    }

    static boolean matchesMagic(String type, byte[] image) {
        return switch (type) {
            case "image/jpeg" -> startsWith(image, 0, JPEG_MAGIC);
            case "image/png" -> startsWith(image, 0, PNG_MAGIC);
            case "image/webp" -> startsWith(image, 0, RIFF) && startsWith(image, 8, WEBP);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (data.length < offset + prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) return false;
        }
        return true;
    }

    private PC lock(Long pcId) {
        return pcRepository.findByIdForUpdate(pcId)
                .orElseThrow(() -> new PCNotFoundException("PC not found with id " + pcId));
    }
}
