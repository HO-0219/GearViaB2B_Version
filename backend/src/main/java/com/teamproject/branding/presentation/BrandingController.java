package com.teamproject.branding.presentation;

import com.teamproject.branding.application.BrandingService;
import com.teamproject.branding.application.dto.BrandingDtos.BrandingResponse;
import com.teamproject.resource.storage.FileStorage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Publicly readable so the login screen and unauthenticated shell can render the org's branding. */
@RestController
@RequestMapping("/api/v1/branding")
public class BrandingController {
    private final BrandingService branding;
    public BrandingController(BrandingService branding) { this.branding = branding; }

    @GetMapping
    BrandingResponse current() { return branding.current(); }

    @GetMapping("/logo")
    ResponseEntity<byte[]> logo() {
        FileStorage.StoredFile file = branding.logo();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300").body(file.content());
    }
}
