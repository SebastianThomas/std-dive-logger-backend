package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.repository.EmailRepository;
import ch.sthomas.stddivelogger.model.entity.EmailEntity;
import ch.sthomas.stddivelogger.model.user.Email;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailDataService {
    private final EmailRepository emailRepository;

    public EmailDataService(final EmailRepository emailRepository) {
        this.emailRepository = emailRepository;
    }

    @Transactional
    public List<Email> findAndUpdateOutstandingEmails() {
        return emailRepository.updateOutstandingEmailsAndGet().stream()
                .map(EmailEntity::toRecord)
                .toList();
    }
}
