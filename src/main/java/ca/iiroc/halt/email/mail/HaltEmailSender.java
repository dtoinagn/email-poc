package ca.iiroc.halt.email.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper over {@link JavaMailSender}. Anything this throws propagates all the way up to the Kafka
 * listener, whose error handler retries with backoff and, once exhausted, logs the failure to the
 * dedicated error log file (design doc §07).
 */
@Component
@RequiredArgsConstructor
public class HaltEmailSender {

    private final JavaMailSender mailSender;

    public void send(EmailContent content) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(content.from());
            helper.setTo(content.to());
            if (content.bcc() != null && !content.bcc().isBlank()) {
                helper.setBcc(content.bcc());
            }
            helper.setSubject(content.subject());
            helper.setText(content.htmlBody(), true);
        } catch (MessagingException e) {
            throw new MailPreparationException("Failed to build halt notification email", e);
        }
        mailSender.send(message);
    }
}
