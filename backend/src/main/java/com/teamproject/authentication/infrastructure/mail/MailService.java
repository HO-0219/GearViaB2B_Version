package com.teamproject.authentication.infrastructure.mail;

import org.slf4j.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private final MailSenderFactory factory;
    public MailService(MailSenderFactory factory) { this.factory = factory; }
    public void sendBestEffort(String to, String subject, String body) {
        try {
        var configured = factory.createSender();
        if (!configured.enabled()) {
            log.info("[LOCAL MAIL] recipient={} subject={} bodyLength={} (content redacted)",
                    maskRecipient(to), subject, body == null ? 0 : body.length());
            return;
        }
        var message = new SimpleMailMessage();
        message.setFrom(configured.fromAddress()); message.setTo(to); message.setSubject(subject); message.setText(body);
            configured.sender().send(message);
        } catch (RuntimeException exception) {
            log.error("Mail delivery failed. recipient={} subject={} error={} message={}",
                    maskRecipient(to), subject, exception.getClass().getSimpleName(), exception.getMessage());
            log.debug("Mail delivery failure detail", exception);
        }
    }

    public boolean sendHtmlBestEffort(String to, String subject, String html) {
        try {
        var configured = factory.createSender();
        if (!configured.enabled()) {
            log.info("[LOCAL MAIL] recipient={} subject={} bodyLength={} contentType=text/html (content redacted)",
                    maskRecipient(to), subject, html == null ? 0 : html.length());
            return true;
        }
            var message = configured.sender().createMimeMessage();
            var helper = new MimeMessageHelper(message, false, java.nio.charset.StandardCharsets.UTF_8.name());
            helper.setFrom(configured.fromAddress()); helper.setTo(to); helper.setSubject(subject); helper.setText(html, true);
            configured.sender().send(message);
            return true;
        } catch (RuntimeException | jakarta.mail.MessagingException exception) {
            log.error("HTML mail delivery failed. recipient={} subject={} error={}",
                    maskRecipient(to), subject, exception.getClass().getSimpleName());
            return false;
        }
    }

    private String maskRecipient(String recipient) {
        int at = recipient.indexOf('@');
        if (at <= 0) return "***";
        return recipient.charAt(0) + "***" + recipient.substring(at);
    }
}
