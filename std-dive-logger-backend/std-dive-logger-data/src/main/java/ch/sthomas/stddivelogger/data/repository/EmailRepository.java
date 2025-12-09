package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.EmailEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailRepository extends JpaRepository<EmailEntity, Long> {}
