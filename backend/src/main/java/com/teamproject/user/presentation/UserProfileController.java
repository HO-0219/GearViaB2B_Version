package com.teamproject.user.presentation;

import com.teamproject.user.application.UserProfileService;
import com.teamproject.user.application.dto.UserProfileDtos.ProfileResponse;
import com.teamproject.user.application.dto.UserProfileDtos.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserProfileController {
    private final UserProfileService profiles;

    public UserProfileController(UserProfileService profiles) { this.profiles = profiles; }

    @GetMapping
    ProfileResponse get(Authentication authentication) {
        return profiles.get((Long) authentication.getPrincipal());
    }

    @PatchMapping
    ProfileResponse update(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update((Long) authentication.getPrincipal(), request);
    }

    @PostMapping("/image")
    ProfileResponse uploadImage(Authentication authentication, @RequestPart("file") MultipartFile file) {
        return profiles.uploadImage((Long) authentication.getPrincipal(), file);
    }
}
