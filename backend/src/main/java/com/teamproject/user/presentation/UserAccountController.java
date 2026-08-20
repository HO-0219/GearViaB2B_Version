package com.teamproject.user.presentation;

import com.teamproject.user.application.UserAccountService;
import com.teamproject.user.application.dto.UserAccountDtos.ChangePasswordRequest;
import com.teamproject.user.application.dto.UserAccountDtos.WithdrawRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserAccountController {
    private final UserAccountService accounts;

    public UserAccountController(UserAccountService accounts) { this.accounts = accounts; }

    @PutMapping("/password")
    ResponseEntity<Void> changePassword(Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        accounts.changePassword((Long) authentication.getPrincipal(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    ResponseEntity<Void> withdraw(Authentication authentication,
            @Valid @RequestBody WithdrawRequest request) {
        accounts.withdraw((Long) authentication.getPrincipal(), request);
        return ResponseEntity.noContent().build();
    }
}
