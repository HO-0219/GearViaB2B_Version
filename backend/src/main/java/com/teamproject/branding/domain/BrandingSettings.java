package com.teamproject.branding.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "branding_settings")
public class BrandingSettings {
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;
    @Column(name = "organization_name", length = 80)
    private String organizationName;
    @Column(name = "logo_storage_key", length = 500)
    private String logoStorageKey;
    @Column(name = "logo_content_type", length = 100)
    private String logoContentType;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected BrandingSettings() {}

    public BrandingSettings(String organizationName) {
        this.id = SINGLETON_ID;
        this.organizationName = organizationName;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateName(String organizationName) {
        this.organizationName = organizationName;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateLogo(String storageKey, String contentType) {
        this.logoStorageKey = storageKey;
        this.logoContentType = contentType;
        this.updatedAt = LocalDateTime.now();
    }

    public void removeLogo() {
        this.logoStorageKey = null;
        this.logoContentType = null;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getOrganizationName() { return organizationName; }
    public String getLogoStorageKey() { return logoStorageKey; }
    public String getLogoContentType() { return logoContentType; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
