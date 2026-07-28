package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.user.Email;

import org.jspecify.annotations.Nullable;
import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.Optional;

@Entity
@Table(name = "t_email")
public class EmailEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_email_id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "receiver", referencedColumnName = "email")
    private UserEntity receiver;

    @Column(name = "original_receiver", nullable = false, updatable = false)
    private String originalReceiver;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "sending", nullable = false)
    private boolean sending;

    @Column(name = "sent_at")
    private @Nullable OffsetDateTime sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public EmailEntity() {}

    public EmailEntity(final UserEntity receiver, final String subject, final String content) {
        this.receiver = receiver;
        this.originalReceiver = receiver.getEmail();
        this.subject = subject;
        this.content = content;
    }

    public Email toRecord() {
        return new Email(
                receiver.getEmail(),
                subject,
                content,
                Optional.ofNullable(sentAt).map(OffsetDateTime::toInstant),
                sending);
    }
}
