package com.teamproject.authentication.presentation;

import com.teamproject.authentication.application.RecoveryService;
import com.teamproject.authentication.application.dto.RecoveryDtos.PasswordResetConfirmRequest;
import com.teamproject.authentication.application.dto.RecoveryDtos.PasswordResetRequest;
import com.teamproject.authentication.application.dto.RecoveryDtos.UsernameReminderRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class RecoveryController {
    private final RecoveryService recovery;
    public RecoveryController(RecoveryService recovery) { this.recovery = recovery; }

    @PostMapping("/username-reminders")
    ResponseEntity<Void> remindUsername(@Valid @RequestBody UsernameReminderRequest request) {
        recovery.remindUsername(request.email());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/password-resets")
    ResponseEntity<Void> requestReset(@Valid @RequestBody PasswordResetRequest request) {
        recovery.requestPasswordReset(request.email());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/password-resets/confirm")
    ResponseEntity<Void> reset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        recovery.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}
