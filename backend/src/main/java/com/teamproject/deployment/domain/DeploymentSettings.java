package com.teamproject.deployment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;

/**
 * Single-row table holding the approved public URL and certificate metadata.
 * Private keys and certificate bodies are never stored here.
 */
@Entity
@Table(name = "deployment_settings")
public class DeploymentSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "public_url", nullable = false, length = 255)
    private String publicUrl;

    @Column(name = "certificate_issuer", length = 255)
    private String certificateIssuer;

    @Column(name = "certificate_not_after")
    private LocalDateTime certificateNotAfter;

    @Column(name = "certificate_sans", length = 1024)
    private String certificateSans;

    @Column(name = "apply_version", nullable = false)
    private long applyVersion;

    @Column(nullable = false, length = 20)
    private String status = "BOOTSTRAP";

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected DeploymentSettings() {
    }

    public DeploymentSettings(String publicUrl) {
        this.id = SINGLETON_ID;
        this.publicUrl = canonicalPublicUrl(publicUrl);
        this.status = "ACTIVE";
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Validates the public URL rules: HTTPS scheme, a host, no user info, no path,
     * no query or fragment, and no explicit default HTTPS port.
     */
    public static URI validatePublicUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("public URL must not be blank");
        }
        URI uri;
        try {
            uri = new URI(value.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("public URL is not a valid URI: " + value, e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("public URL must use HTTPS");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("public URL must contain a host");
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("public URL must not contain user info");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("public URL must not contain a query or fragment");
        }
        if (uri.getPort() == 443) {
            throw new IllegalArgumentException("public URL must omit the default HTTPS port");
        }
        String path = uri.getRawPath();
        if (path != null && !path.isEmpty() && !path.equals("/")) {
            throw new IllegalArgumentException("public URL must not contain a path");
        }
        return uri;
    }

    private static String canonicalPublicUrl(String value) {
        URI uri = validatePublicUrl(value);
        String host = uri.getHost().toLowerCase();
        return uri.getPort() < 0 ? "https://" + host : "https://" + host + ":" + uri.getPort();
    }

    public URI publicUri() {
        return URI.create(publicUrl);
    }

    public Long getId() {
        return id;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public String getCertificateIssuer() {
        return certificateIssuer;
    }

    public LocalDateTime getCertificateNotAfter() {
        return certificateNotAfter;
    }

    public String getCertificateSans() {
        return certificateSans;
    }

    public long getApplyVersion() {
        return applyVersion;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
