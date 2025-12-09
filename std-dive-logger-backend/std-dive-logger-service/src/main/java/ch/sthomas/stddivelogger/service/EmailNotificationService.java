package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.EmailDataService;
import ch.sthomas.stddivelogger.model.notification.*;
import ch.sthomas.stddivelogger.model.user.Email;

import jakarta.mail.SendFailedException;
import jakarta.validation.constraints.NotBlank;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Profile("email")
public class EmailNotificationService implements NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationService.class);
    private final String email;
    private final MailSender mailSender;
    private final EmailDataService emailDataService;

    public EmailNotificationService(
            @Value("${ch.sthomas.stddivelogger.email.address}")
                    @jakarta.validation.constraints.Email
                    @NotBlank
                    final String email,
            final MailSender mailSender,
            final EmailDataService emailDataService) {
        this.email = email;
        this.mailSender = mailSender;
        this.emailDataService = emailDataService;
    }

    @Override
    public <T> NotificationResult sendNotification(final NotificationPayload<T> payload) {
        if (payload instanceof final EmailNotificationPayload p) {
            return sendEmail(p);
        }
        throw new IllegalArgumentException("Expected email notification payload, got " + payload);
    }

    public Pair<Long, Long> sendOutstandingEmails() {
        final var emails = emailDataService.findAndUpdateOutstandingEmails();
        final var sent =
                emails.stream()
                        .parallel()
                        .map(Email::toPayload)
                        .map(this::sendEmail)
                        .filter(r -> r instanceof SuccessfulNotificationResult)
                        .count();
        return Pair.of((long) emails.size(), sent);
    }

    public NotificationResult sendEmail(final EmailNotificationPayload p) {
        try {
            // I hope this is not optimized out, should never trigger, but it could be thrown by
            // underlying API calls, so we want the catch
            if (email.isEmpty()) {
                throw new SendFailedException();
            }

            final var message = new SimpleMailMessage();
            message.setFrom(email);
            message.setTo(p.receiver());
            message.setSubject(p.subject());
            message.setText(p.body());
            mailSender.send(message);
            return new EmailNotificationResult(Instant.now());
        } catch (final SendFailedException | MailSendException e) {
            logger.info("Send Failed");
            return new EmailWithExceptionNotificationResult(e);
        }
    }
}
