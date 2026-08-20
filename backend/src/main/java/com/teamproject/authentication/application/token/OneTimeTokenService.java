package com.teamproject.authentication.application.token;

import com.teamproject.authentication.domain.token.OneTimeToken;
import com.teamproject.authentication.domain.token.OneTimeTokenRepository;
import com.teamproject.authentication.infrastructure.crypto.HashService;
import com.teamproject.common.exception.ApplicationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class OneTimeTokenService {
    private final SecureRandom random = new SecureRandom();
    private final OneTimeTokenRepository repository;
    private final HashService hashService;
    private final long verificationMinutes;
    private final long resetMinutes;
    public OneTimeTokenService(OneTimeTokenRepository repository, HashService hashService,
            @Value("${app.mail.verification-minutes}") long verificationMinutes,
            @Value("${app.mail.password-reset-minutes}") long resetMinutes) {
        this.repository = repository; this.hashService = hashService;
        this.verificationMinutes = verificationMinutes; this.resetMinutes = resetMinutes;
    }
    @Transactional
    public String issueCode(String email) {
        rateLimit(email, OneTimeToken.Purpose.SIGNUP);
        String code = "%06d".formatted(random.nextInt(1_000_000));
        repository.save(new OneTimeToken(email, OneTimeToken.Purpose.SIGNUP, hashService.sha256(code), LocalDateTime.now().plusMinutes(verificationMinutes)));
        return code;
    }
    @Transactional
    public String issueResetToken(String email) {
        rateLimit(email, OneTimeToken.Purpose.PASSWORD_RESET);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random.generateSeed(32));
        repository.save(new OneTimeToken(email, OneTimeToken.Purpose.PASSWORD_RESET, hashService.sha256(token), LocalDateTime.now().plusMinutes(resetMinutes)));
        return token;
    }
    @Transactional(readOnly = true)
    public void verifyCode(String email, String code) { usable(email, OneTimeToken.Purpose.SIGNUP, code); }
    @Transactional
    public void consumeCode(String email, String code) { usable(email, OneTimeToken.Purpose.SIGNUP, code).use(LocalDateTime.now()); }
    @Transactional
    public void consumeResetToken(String email, String token) { usable(email, OneTimeToken.Purpose.PASSWORD_RESET, token).use(LocalDateTime.now()); }

    private OneTimeToken usable(String email, OneTimeToken.Purpose purpose, String raw) {
        return repository.findFirstByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(email, purpose)
                .filter(value -> value.isUsable(hashService.sha256(raw), LocalDateTime.now()))
                .orElseThrow(() -> new ApplicationException("TOKEN_INVALID", HttpStatus.BAD_REQUEST, "인증 정보가 올바르지 않거나 만료되었습니다."));
    }
    private void rateLimit(String email, OneTimeToken.Purpose purpose) {
        repository.findFirstByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(email, purpose)
                .filter(token -> token.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(1)))
                .ifPresent(token -> { throw new ApplicationException("TOO_MANY_REQUESTS", HttpStatus.TOO_MANY_REQUESTS, "1분 후 다시 요청해 주세요."); });
    }
}
