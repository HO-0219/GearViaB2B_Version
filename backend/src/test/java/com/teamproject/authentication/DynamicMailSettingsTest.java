package com.teamproject.authentication;

import com.teamproject.admin.security.AdminMfaCipher;
import com.teamproject.authentication.infrastructure.mail.MailSenderFactory;
import com.teamproject.authentication.infrastructure.mail.MailSettings;
import com.teamproject.authentication.infrastructure.mail.MailSettingsRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicMailSettingsTest {
    private final MailSettingsRepository repository = mock(MailSettingsRepository.class);
    private final AdminMfaCipher cipher = mock(AdminMfaCipher.class);

    @Test
    void createsSenderFromDatabaseSettingsInsteadOfDeploymentDefaults() {
        MailSettings settings = new MailSettings("smtp.company.test", 587, "mailer", "encrypted",
                true, true, "noreply@company.test", false);
        when(repository.findById(MailSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(cipher.decrypt("encrypted")).thenReturn("secret");
        MailSenderFactory factory = new MailSenderFactory(repository, cipher,
                "env.test", 1025, "env-user", "env-secret", false, false,
                "env@example.test", true);

        var configured = factory.createSender();

        assertThat(configured.sender().getHost()).isEqualTo("smtp.company.test");
        assertThat(configured.sender().getPort()).isEqualTo(587);
        assertThat(configured.sender().getUsername()).isEqualTo("mailer");
        assertThat(configured.sender().getPassword()).isEqualTo("secret");
        assertThat(configured.sender().getJavaMailProperties())
                .containsEntry("mail.smtp.auth", "true")
                .containsEntry("mail.smtp.starttls.enable", "true");
        assertThat(configured.fromAddress()).isEqualTo("noreply@company.test");
        assertThat(configured.enabled()).isFalse();
        assertThat(configured.source()).isEqualTo(MailSenderFactory.Source.DATABASE);
    }

    @Test
    void fallsBackToDeploymentSettingsOnlyWhenDatabaseRowDoesNotExist() {
        when(repository.findById(MailSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        MailSenderFactory factory = new MailSenderFactory(repository, cipher,
                "env.test", 1025, "env-user", "env-secret", false, false,
                "env@example.test", true);

        var configured = factory.createSender();

        assertThat(configured.sender().getHost()).isEqualTo("env.test");
        assertThat(configured.fromAddress()).isEqualTo("env@example.test");
        assertThat(configured.enabled()).isTrue();
        assertThat(configured.source()).isEqualTo(MailSenderFactory.Source.ENVIRONMENT);
    }
}
