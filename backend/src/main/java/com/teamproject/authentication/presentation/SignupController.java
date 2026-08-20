package com.teamproject.authentication.presentation;

import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupResponse;
import com.teamproject.authentication.application.dto.SignupDtos.VerificationConfirmRequest;
import com.teamproject.authentication.application.dto.SignupDtos.VerificationEmailRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class SignupController {
    private final SignupService signup;
    public SignupController(SignupService signup) { this.signup = signup; }

    @PostMapping("/email-verifications")
    ResponseEntity<Void> sendVerification(@Valid @RequestBody VerificationEmailRequest request) {
        signup.sendVerification(request.email());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/email-verifications/confirm")
    ResponseEntity<Void> confirm(@Valid @RequestBody VerificationConfirmRequest request) {
        signup.verifyCode(request.email(), request.code());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/signup")
    SignupResponse signup(@Valid @RequestBody SignupRequest request) { return signup.signup(request); }
}
