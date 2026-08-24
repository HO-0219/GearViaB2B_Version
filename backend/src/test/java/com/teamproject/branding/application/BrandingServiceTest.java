package com.teamproject.branding.application;

import com.teamproject.branding.domain.BrandingSettings;
import com.teamproject.branding.domain.BrandingSettingsRepository;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.resource.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BrandingServiceTest {
    private final BrandingSettingsRepository settings = mock(BrandingSettingsRepository.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final BrandingService service = new BrandingService(settings, storage);

    @BeforeEach
    void stubSave() {
        when(settings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void currentFallsBackToTheDefaultNameWhenNothingIsConfigured() {
        when(settings.findById(BrandingSettings.SINGLETON_ID)).thenReturn(Optional.empty());

        var response = service.current();

        assertThat(response.organizationName()).isEqualTo("B2BGearVia");
        assertThat(response.hasLogo()).isFalse();
    }

    @Test
    void updateTrimsAndPersistsTheOrganizationName() {
        when(settings.findById(BrandingSettings.SINGLETON_ID)).thenReturn(Optional.empty());

        var response = service.update("  Acme Corp  ", null, false);

        assertThat(response.organizationName()).isEqualTo("Acme Corp");
    }

    @Test
    void updateRejectsAnOverlongOrganizationName() {
        when(settings.findById(BrandingSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        String tooLong = "x".repeat(81);

        assertThatThrownBy(() -> service.update(tooLong, null, false))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("80자");
    }

    @Test
    void updateStoresAValidPngLogoAndRecordsItsContentType() {
        BrandingSettings existing = new BrandingSettings("Acme");
        when(settings.findById(BrandingSettings.SINGLETON_ID)).thenReturn(Optional.of(existing));
        byte[] pngBytes = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x01, 0x02};
        MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", pngBytes);

        service.update(null, logo, false);

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(storage).put(eq("branding/logo"), captor.capture(), eq("image/png"));
        assertThat(captor.getValue()).isEqualTo(pngBytes);
        assertThat(existing.getLogoStorageKey()).isEqualTo("branding/logo");
    }

    @Test
    void updateRejectsAFileWhoseBytesDoNotMatchAKnownImageSignature() {
        when(settings.findById(BrandingSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        MockMultipartFile fake = new MockMultipartFile("logo", "logo.png", "image/png", "not-an-image".getBytes());

        assertThatThrownBy(() -> service.update(null, fake, false))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("PNG 또는 JPG");
        verify(storage, never()).put(any(), any(), any());
    }

    @Test
    void updateRejectsALogoLargerThanTwoMegabytes() {
        when(settings.findById(BrandingSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", oversized);

        assertThatThrownBy(() -> service.update(null, logo, false))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("2MB");
    }

    @Test
    void updateRemovesTheStoredLogoWhenRemoveLogoIsRequested() {
        BrandingSettings existing = new BrandingSettings("Acme");
        existing.updateLogo("branding/logo", "image/png");
        when(settings.findById(BrandingSettings.SINGLETON_ID)).thenReturn(Optional.of(existing));

        service.update(null, null, true);

        verify(storage).delete("branding/logo");
        assertThat(existing.getLogoStorageKey()).isNull();
    }

    @Test
    void logoThrowsNotFoundWhenNoLogoIsConfigured() {
        when(settings.findById(BrandingSettings.SINGLETON_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(service::logo).isInstanceOf(ApplicationException.class);
    }

    @Test
    void logoReturnsTheStoredFileWhenConfigured() {
        BrandingSettings existing = new BrandingSettings("Acme");
        existing.updateLogo("branding/logo", "image/png");
        when(settings.findById(BrandingSettings.SINGLETON_ID)).thenReturn(Optional.of(existing));
        FileStorage.StoredFile stored = new FileStorage.StoredFile(new byte[]{1, 2, 3}, "image/png");
        when(storage.get("branding/logo")).thenReturn(stored);

        assertThat(service.logo()).isEqualTo(stored);
    }
}
