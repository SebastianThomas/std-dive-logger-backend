package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.notification.AccountRequest;
import ch.sthomas.stddivelogger.model.notification.AccountRequestType;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Entity
@Table(name = "t_account_request")
@SuppressWarnings("NullAway.Init")
public class AccountRequestEntity {
    @Id
    @Column(name = "pk_account_request_id", updatable = false, nullable = false)
    private String id;

    @OneToOne
    @JoinColumn(name = "fk_user_id", updatable = false)
    private UserEntity user;

    @OneToOne
    @JoinColumn(name = "fk_email_id", updatable = false)
    private EmailEntity email;

    @Column(name = "request_type", nullable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    private AccountRequestType requestType;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private OffsetDateTime validUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public AccountRequestEntity() {}

    public AccountRequestEntity(
            final String id,
            final UserEntity user,
            final EmailEntity email,
            final AccountRequestType requestType,
            final Instant validUntil) {
        this.id = id;
        this.user = user;
        this.email = email;
        this.requestType = requestType;
        this.validUntil = validUntil.atOffset(ZoneOffset.UTC);
    }

    public Optional<AccountRequest> toRecord() {
        if (id == null
                || validUntil == null
                || validUntil.isBefore(Instant.now().atOffset(ZoneOffset.UTC))) {
            return Optional.empty();
        }
        return Optional.of(new AccountRequest(user.toRecord(), email.toRecord(), requestType));
    }
}
