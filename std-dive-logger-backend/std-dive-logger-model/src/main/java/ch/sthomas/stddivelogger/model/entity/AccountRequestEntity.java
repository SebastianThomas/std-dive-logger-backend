package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.notification.AccountRequest;
import ch.sthomas.stddivelogger.model.notification.AccountRequestType;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "t_account_request")
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public AccountRequestEntity() {}

    public AccountRequestEntity(
            final String id,
            final UserEntity user,
            final EmailEntity email,
            final AccountRequestType requestType) {
        this.id = id;
        this.user = user;
        this.email = email;
        this.requestType = requestType;
    }

    public AccountRequest toRecord() {
        return new AccountRequest(user.toRecord(), email.toRecord(), requestType);
    }
}
