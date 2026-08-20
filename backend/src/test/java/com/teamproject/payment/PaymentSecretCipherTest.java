package com.teamproject.payment;

import com.teamproject.payment.infrastructure.PaymentSecretCipher;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSecretCipherTest {
    @Test
    void billingKeyIsAuthenticatedEncryptedAndCanBeRecovered() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        PaymentSecretCipher cipher = new PaymentSecretCipher(key);

        String encrypted = cipher.encrypt("billing-secret-value");

        assertThat(encrypted).doesNotContain("billing-secret-value");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("billing-secret-value");
        assertThat(cipher.encrypt("billing-secret-value")).isNotEqualTo(encrypted);
    }
}
