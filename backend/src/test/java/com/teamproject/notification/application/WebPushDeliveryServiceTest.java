package com.teamproject.notification.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebPushDeliveryServiceTest {

    @Test
    void removesBase64UrlPaddingFromCryptoKeyParameters() {
        assertThat(WebPushDeliveryService.normalizeCryptoKey(
                "dh=browser-key==;p256ecdsa=application-server-key="))
                .isEqualTo("dh=browser-key;p256ecdsa=application-server-key");
    }
}
