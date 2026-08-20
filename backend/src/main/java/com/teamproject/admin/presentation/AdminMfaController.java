package com.teamproject.admin.presentation;

import com.teamproject.admin.application.AdminMfaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/mfa")
public class AdminMfaController {
    private final AdminMfaService mfa;
    public AdminMfaController(AdminMfaService mfa) { this.mfa = mfa; }
    @GetMapping("/status")
    AdminMfaService.Status status(Authentication authentication) {
        boolean verified = authentication.getAuthorities().stream()
                .anyMatch(value -> "MFA_VERIFIED".equals(value.getAuthority()));
        return mfa.status((Long) authentication.getPrincipal(), verified);
    }
    @PostMapping("/setup")
    AdminMfaService.Setup setup(Authentication authentication) {
        return mfa.setup((Long) authentication.getPrincipal());
    }
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirm(Authentication authentication, @Valid @RequestBody ConfirmRequest request) {
        mfa.confirm((Long) authentication.getPrincipal(), request.code());
    }
    public record ConfirmRequest(@NotBlank String code) {}
}
