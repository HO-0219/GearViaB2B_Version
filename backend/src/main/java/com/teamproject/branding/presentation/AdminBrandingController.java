package com.teamproject.branding.presentation;

import com.teamproject.branding.application.BrandingService;
import com.teamproject.branding.application.dto.BrandingDtos.BrandingResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/branding")
public class AdminBrandingController {
    private final BrandingService branding;
    public AdminBrandingController(BrandingService branding) { this.branding = branding; }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    BrandingResponse update(@RequestParam(required = false) String organizationName,
            @RequestParam(required = false) MultipartFile logo,
            @RequestParam(defaultValue = "false") boolean removeLogo) {
        return branding.update(organizationName, logo, removeLogo);
    }
}
