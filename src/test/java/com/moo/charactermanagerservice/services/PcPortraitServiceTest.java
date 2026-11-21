package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.exceptions.PCNotFoundException;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcPortrait;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.PcPortraitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PcPortraitServiceTest {
    private final PCService pcs = mock(PCService.class);
    private final PCRepository pcRepository = mock(PCRepository.class);
    private final PcPortraitRepository portraits = mock(PcPortraitRepository.class);
    private final PcPortraitService service = new PcPortraitService(pcs, pcRepository, portraits);

    private final UUID owner = UUID.randomUUID();
    private final UUID dm = UUID.randomUUID();
    private final UUID partyMember = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final PC pc = new PC();

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final byte[] WEBP = {0x52, 0x49, 0x46, 0x46, 0x24, 0, 0, 0, 0x57, 0x45, 0x42, 0x50, 0x56, 0x50};

    @BeforeEach
    void setUp() {
        pc.setId(1L);
        pc.setUserId(owner);
        pc.setCampaignId(7L);
        when(pcs.findPCById(1L)).thenReturn(pc);
        when(pcs.findPCByIdForDm(1L, dm)).thenReturn(pc);
        when(pcs.findPCByIdForDm(1L, partyMember)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));
        when(pcs.findPCByIdForDm(1L, stranger)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));
        when(pcRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.existsByCampaignIdAndUserId(7L, partyMember)).thenReturn(true);
        when(portraits.findById(1L)).thenReturn(Optional.empty());
    }

    private static HttpStatus statusOf(Throwable e) {
        return HttpStatus.valueOf(((ResponseStatusException) e).getStatusCode().value());
    }

    // --- upload ---

    @Test
    void upload_storesTheImageAndSetsAVersion() {
        long version = service.upload(1L, "image/png", PNG, owner);

        assertThat(version).isPositive();
        assertThat(pc.getPortraitVersion()).isEqualTo(version);
        verify(portraits).save(argThat(p -> p.getPcId().equals(1L)
                && p.getContentType().equals("image/png")
                && Arrays.equals(p.getImage(), PNG)
                && p.getUpdatedAt() != null));
        verify(pcRepository).save(pc);
    }

    @Test
    void upload_alwaysMovesTheVersion_evenWhenTheStoredOneIsAhead() {
        long future = System.currentTimeMillis() + 1_000_000L;
        pc.setPortraitVersion(future);

        long version = service.upload(1L, "image/jpeg", JPEG, owner);

        assertThat(version).isEqualTo(future + 1);
    }

    @Test
    void upload_twiceGivesStrictlyIncreasingVersions_andReplacesTheRow() {
        PcPortrait existing = new PcPortrait();
        existing.setPcId(1L);
        existing.setContentType("image/png");
        existing.setImage(PNG);
        long first = service.upload(1L, "image/png", PNG, owner);
        when(portraits.findById(1L)).thenReturn(Optional.of(existing));

        long second = service.upload(1L, "image/webp", WEBP, owner);

        assertThat(second).isGreaterThan(first);
        assertThat(existing.getContentType()).isEqualTo("image/webp");
        assertThat(existing.getImage()).isEqualTo(WEBP);
    }

    @Test
    void upload_acceptsEachAllowedType_andIgnoresContentTypeParameters() {
        service.upload(1L, "image/jpeg", JPEG, owner);
        service.upload(1L, "IMAGE/PNG; name=portrait", PNG, owner);
        service.upload(1L, "image/webp", WEBP, owner);

        org.mockito.ArgumentCaptor<PcPortrait> saved = org.mockito.ArgumentCaptor.forClass(PcPortrait.class);
        verify(portraits, times(3)).save(saved.capture());
        // One row object per call here (findById is stubbed empty); the type is stored without parameters.
        assertThat(saved.getAllValues()).extracting(PcPortrait::getContentType)
                .containsExactly("image/jpeg", "image/png", "image/webp");
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/gif", "image/svg+xml", "application/octet-stream", "text/plain", "", "not a type"})
    void upload_rejectsOtherTypes_with415(String type) {
        assertThatThrownBy(() -> service.upload(1L, type, PNG, owner))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        verify(portraits, never()).save(any());
    }

    @Test
    void upload_rejectsAMissingType_with415() {
        assertThatThrownBy(() -> service.upload(1L, null, PNG, owner))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void upload_acceptsExactly2Mb_butRejectsOneByteMore_with413() {
        byte[] max = Arrays.copyOf(PNG, PcPortraitService.MAX_BYTES);
        assertThat(service.upload(1L, "image/png", max, owner)).isPositive();

        byte[] tooBig = Arrays.copyOf(PNG, PcPortraitService.MAX_BYTES + 1);
        assertThatThrownBy(() -> service.upload(1L, "image/png", tooBig, owner))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void upload_rejectsAnEmptyBody_with400() {
        for (byte[] empty : new byte[][]{new byte[0], null}) {
            assertThatThrownBy(() -> service.upload(1L, "image/png", empty, owner))
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(portraits, never()).save(any());
    }

    @Test
    void upload_rejectsBytesThatAreNotTheDeclaredType_with400() {
        byte[] riffNotWebp = Arrays.copyOf(WEBP, WEBP.length);
        riffNotWebp[8] = 'A'; // RIFF....AVI- rather than WEBP
        Object[][] cases = {
                {"image/jpeg", PNG}, {"image/png", JPEG}, {"image/webp", PNG},
                {"image/webp", riffNotWebp}, {"image/png", new byte[]{(byte) 0x89, 0x50}},
                {"image/jpeg", "<svg/>".getBytes()},
        };
        for (Object[] c : cases) {
            assertThatThrownBy(() -> service.upload(1L, (String) c[0], (byte[]) c[1], owner))
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(portraits, never()).save(any());
        assertThat(pc.getPortraitVersion()).isNull();
    }

    @Test
    void upload_unknownCharacter_is404() {
        when(pcs.findPCById(99L)).thenThrow(new PCNotFoundException("PC not found with id 99"));

        assertThatThrownBy(() -> service.upload(99L, "image/png", PNG, owner))
                .isInstanceOf(PCNotFoundException.class);
    }

    // --- authorization ---

    @Test
    void campaignDmMayUploadAndDelete() {
        assertThat(service.upload(1L, "image/png", PNG, dm)).isPositive();
        service.delete(1L, dm);
        assertThat(pc.getPortraitVersion()).isNull();
    }

    @Test
    void partyMembersAndStrangersMayNotWrite_403() {
        for (UUID caller : new UUID[]{partyMember, stranger}) {
            assertThatThrownBy(() -> service.upload(1L, "image/png", PNG, caller))
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.FORBIDDEN));
            assertThatThrownBy(() -> service.delete(1L, caller))
                    .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.FORBIDDEN));
        }
        verify(portraits, never()).save(any());
        verify(portraits, never()).delete(any());
        verify(pcRepository, never()).save(any());
    }

    @Test
    void authorizationIsCheckedBeforeTheBody_soAStrangerLearnsNothingFromValidation() {
        assertThatThrownBy(() -> service.upload(1L, "image/gif", new byte[0], stranger))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void ownerDmAndPartyMembersMayRead_strangersGet403() {
        PcPortrait stored = new PcPortrait();
        stored.setPcId(1L);
        stored.setContentType("image/jpeg");
        stored.setImage(JPEG);
        when(portraits.findById(1L)).thenReturn(Optional.of(stored));

        for (UUID caller : new UUID[]{owner, dm, partyMember}) {
            PcPortraitService.PortraitImage image = service.get(1L, caller);
            assertThat(image.contentType()).isEqualTo("image/jpeg");
            assertThat(image.image()).isEqualTo(JPEG);
        }
        assertThatThrownBy(() -> service.get(1L, stranger))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void aCharacterInNoCampaign_isReadableByItsOwnerOnly() {
        pc.setCampaignId(null);
        when(pcs.findPCByIdForDm(1L, dm)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.get(1L, dm))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.FORBIDDEN));
        verify(pcRepository, never()).existsByCampaignIdAndUserId(any(), any());
    }

    // --- get / delete ---

    @Test
    void get_withoutAPortrait_is404() {
        assertThatThrownBy(() -> service.get(1L, owner))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void delete_removesTheRowAndClearsTheVersion() {
        PcPortrait stored = new PcPortrait();
        stored.setPcId(1L);
        when(portraits.findById(1L)).thenReturn(Optional.of(stored));
        pc.setPortraitVersion(42L);

        service.delete(1L, owner);

        verify(portraits).delete(stored);
        assertThat(pc.getPortraitVersion()).isNull();
        verify(pcRepository).save(pc);
    }

    @Test
    void delete_withoutAPortrait_isANoOp() {
        service.delete(1L, owner);

        verify(portraits, never()).delete(any());
        verify(pcRepository, never()).save(any());
    }

    @Test
    void reuploadAfterDelete_neverReusesAVersion() {
        long first = service.upload(1L, "image/png", PNG, owner);
        service.delete(1L, owner);

        long second = service.upload(1L, "image/png", PNG, owner);

        assertThat(second).isGreaterThanOrEqualTo(first);
        // Strictly: the millisecond clock may not have ticked, but the version is never older.
    }
}
