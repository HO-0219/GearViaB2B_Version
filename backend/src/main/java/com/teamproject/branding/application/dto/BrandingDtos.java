package com.teamproject.branding.application.dto;

public final class BrandingDtos {
    private BrandingDtos() {}
    public record BrandingResponse(String organizationName, boolean hasLogo) {}
}
