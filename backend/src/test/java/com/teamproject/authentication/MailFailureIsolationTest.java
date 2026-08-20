package com.teamproject.authentication;

import com.teamproject.TeamProjectApplication;
import com.teamproject.authentication.application.RecoveryService;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.authentication.domain.token.OneTimeToken;
import com.teamproject.authentication.domain.token.OneTimeTokenRepository;
import com.teamproject.group.application.GroupInvitationService;
import com.teamproject.group.application.GroupService;
import com.teamproject.group.application.dto.GroupDtos.CreateGroupRequest;
import com.teamproject.group.domain.GroupInvitation;
import com.teamproject.group.domain.GroupInvitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = TeamProjectApplication.class, properties = "app.mail.enabled=true")
class MailFailureIsolationTest {
    @MockBean JavaMailSender sender;
    @Autowired SignupService signup;
    @Autowired RecoveryService recovery;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired OneTimeTokenRepository tokens;
    @Autowired GroupService groups;
    @Autowired GroupInvitationService groupInvitations;
    @Autowired GroupInvitationRepository invitations;

    @BeforeEach
    void failEveryMailDelivery() {
        reset(sender);
        doThrow(new MailSendException("SMTP unavailable"))
                .when(sender).send(any(SimpleMailMessage.class));
    }

    @Test
    void smtpFailureDoesNotRollbackTokensOrGroupInvitation() {
        String verificationEmail = "mail-isolation-verification@example.com";
        signup.sendVerification(verificationEmail);
        assertThat(tokens.findFirstByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(
                verificationEmail, OneTimeToken.Purpose.SIGNUP)).isPresent();

        String accountEmail = "mail-isolation-member@example.com";
        String code = oneTimeTokens.issueCode(accountEmail);
        var account = signup.signup(new SignupRequest(
                "mail_isolation_member", accountEmail, "메일 격리 사용자", "password123!", code));

        recovery.remindUsername(accountEmail);
        recovery.requestPasswordReset(accountEmail);
        assertThat(tokens.findFirstByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(
                accountEmail, OneTimeToken.Purpose.PASSWORD_RESET)).isPresent();

        long groupId = groups.createTeam(account.userId(),
                new CreateGroupRequest("메일 실패 격리 팀", null, "Asia/Seoul")).id();
        var invitation = groupInvitations.invite(account.userId(), groupId, "mail-isolation-invitee@example.com");
        assertThat(invitation.status()).isEqualTo(GroupInvitation.Status.PENDING.name());
        assertThat(invitations.findFirstByGroupIdAndEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                groupId, invitation.email(), GroupInvitation.Status.PENDING)).isPresent();

        verify(sender, times(4)).send(any(SimpleMailMessage.class));
    }
}
