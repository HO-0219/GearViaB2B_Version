package com.teamproject.payment.presentation;

import com.teamproject.payment.application.PaymentService;
import com.teamproject.payment.application.dto.PaymentDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService payments;
    public PaymentController(PaymentService payments) { this.payments = payments; }

    @GetMapping("/config")
    PaymentConfigResponse config(Authentication auth) { return payments.config((Long) auth.getPrincipal()); }
    @GetMapping("/methods")
    List<PaymentMethodResponse> methods(Authentication auth) { return payments.methods((Long) auth.getPrincipal()); }
    @PostMapping("/methods")
    @ResponseStatus(HttpStatus.CREATED)
    PaymentMethodResponse issue(Authentication auth, @Valid @RequestBody IssuePaymentMethodRequest request) {
        return payments.issue((Long) auth.getPrincipal(), request);
    }
    @PostMapping("/methods/{methodId}/test-charge")
    PaymentAttemptResponse testCharge(Authentication auth, @PathVariable Long methodId,
            @Valid @RequestBody TestChargeRequest request) {
        return payments.testCharge((Long) auth.getPrincipal(), methodId, request);
    }
    @GetMapping("/attempts")
    List<PaymentAttemptResponse> attempts(Authentication auth) { return payments.attempts((Long) auth.getPrincipal()); }
    @PostMapping("/attempts/{attemptId}/retry")
    PaymentAttemptResponse retry(Authentication auth, @PathVariable Long attemptId) {
        return payments.retry((Long) auth.getPrincipal(), attemptId);
    }
}
