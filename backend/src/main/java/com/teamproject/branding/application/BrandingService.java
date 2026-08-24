package com.teamproject.branding.application;

import com.teamproject.branding.application.dto.BrandingDtos.BrandingResponse;
import com.teamproject.branding.domain.BrandingSettings;
import com.teamproject.branding.domain.BrandingSettingsRepository;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.resource.storage.FileStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@Service
public class BrandingService {
    private static final String DEFAULT_NAME = "B2BGearVia";
    private static final String LOGO_STORAGE_KEY = "branding/logo";
    private static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;

    private final BrandingSettingsRepository settings;
    private final FileStorage storage;

    public BrandingService(BrandingSettingsRepository settings, FileStorage storage) {
        this.settings = settings;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public BrandingResponse current() {
        return settings.findById(BrandingSettings.SINGLETON_ID)
                .map(this::toResponse)
                .orElse(new BrandingResponse(DEFAULT_NAME, false));
    }

    @Transactional
    public BrandingResponse update(String organizationName, MultipartFile logo, boolean removeLogo) {
        BrandingSettings value = settings.findById(BrandingSettings.SINGLETON_ID)
                .orElseGet(() -> settings.save(new BrandingSettings(null)));
        if (organizationName != null) {
            String trimmed = organizationName.trim();
            if (trimmed.length() > 80) throw invalid("조직/서비스 이름은 80자 이하여야 합니다.");
            value.updateName(trimmed.isBlank() ? null : trimmed);
        }
        if (logo != null && !logo.isEmpty()) {
            byte[] bytes = validateLogo(logo);
            String contentType = signatureContentType(bytes);
            storage.put(LOGO_STORAGE_KEY, bytes, contentType);
            value.updateLogo(LOGO_STORAGE_KEY, contentType);
        } else if (removeLogo && value.getLogoStorageKey() != null) {
            storage.delete(value.getLogoStorageKey());
            value.removeLogo();
        }
        return toResponse(value);
    }

    private BrandingResponse toResponse(BrandingSettings value) {
        return new BrandingResponse(displayName(value.getOrganizationName()), value.getLogoStorageKey() != null);
    }

    @Transactional(readOnly = true)
    public FileStorage.StoredFile logo() {
        BrandingSettings value = settings.findById(BrandingSettings.SINGLETON_ID).orElse(null);
        if (value == null || value.getLogoStorageKey() == null) {
            throw new ApplicationException("BRANDING_LOGO_NOT_SET", HttpStatus.NOT_FOUND, "설정된 로고가 없습니다.");
        }
        return storage.get(value.getLogoStorageKey());
    }

    private String displayName(String organizationName) {
        return organizationName == null || organizationName.isBlank() ? DEFAULT_NAME : organizationName;
    }

    private byte[] validateLogo(MultipartFile file) {
        if (file.getSize() > MAX_LOGO_BYTES) throw invalid("로고 이미지는 2MB 이하여야 합니다.");
        try {
            byte[] bytes = file.getBytes();
            signatureContentType(bytes);
            return bytes;
        } catch (IOException exception) {
            throw invalid("로고 파일을 읽을 수 없습니다.");
        }
    }

    private String signatureContentType(byte[] bytes) {
        if (starts(bytes, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47})) return "image/png";
        if (starts(bytes, new byte[]{(byte) 0xff, (byte) 0xd8})) return "image/jpeg";
        throw invalid("PNG 또는 JPG 이미지만 업로드할 수 있습니다.");
    }

    private boolean starts(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) if (value[index] != prefix[index]) return false;
        return true;
    }

    private ApplicationException invalid(String message) {
        return new ApplicationException("BRANDING_INVALID", HttpStatus.BAD_REQUEST, message);
    }
}
