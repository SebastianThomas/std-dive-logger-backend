package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.user.Email;

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

    @Column(name = "receiver", nullable = false)
    private String receiver;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "sending", nullable = false)
    private boolean sending;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public EmailEntity() {}

    public EmailEntity(final String receiver, final String subject, final String content) {
        this.receiver = receiver;
        this.subject = subject;
        this.content = content;
    }

    public Email toRecord() {
        return new Email(
                receiver,
                subject,
                content,
                Optional.ofNullable(sentAt).map(OffsetDateTime::toInstant),
                sending);
    }
}
