package ch.sthomas.stddivelogger.ws.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class EmailConfig {
    @Bean
    public MailSender mailSender(
            @Value("${ch.sthomas.stddivelogger.email.address}") @Email final String email,
            @Value("${ch.sthomas.stddivelogger.email.password}") @NotBlank final String password,
            @Value("${ch.sthomas.stddivelogger.email.host}") @NotBlank final String smtpHost,
            @Value("${ch.sthomas.stddivelogger.email.port:587}") final int smtpPort) {
        final var sender = new JavaMailSenderImpl();
        sender.setHost(smtpHost);
        sender.setPort(smtpPort);
        sender.setUsername(email);
        sender.setPassword(password);

        final var props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.debug", "true");

        return sender;
    }
}
