package ch.sthomas.stddivegastracker.model.entity;

import ch.sthomas.stddivegastracker.model.payment.StoredPayment;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

/** Money from one member to another to settle balances (DOMAIN §8); deleted if wrong. */
@Entity
@Table(name = "t_payment")
@SuppressWarnings("NullAway.Init")
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_payment_id", nullable = false)
    private Long id;

    @Column(name = "fk_team_id", nullable = false)
    private Long teamId;

    @Column(name = "fk_from_member_id", nullable = false)
    private Long fromMemberId;

    @Column(name = "fk_to_member_id", nullable = false)
    private Long toMemberId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(name = "note", length = 200)
    private @Nullable String note;

    @Column(name = "fk_created_by_member_id", nullable = false)
    private Long createdByMemberId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PaymentEntity() {}

    public PaymentEntity(
            final Long teamId,
            final Long fromMemberId,
            final Long toMemberId,
            final BigDecimal amount,
            final Instant paidAt,
            final @Nullable String note,
            final Long createdByMemberId) {
        this.teamId = teamId;
        this.fromMemberId = fromMemberId;
        this.toMemberId = toMemberId;
        this.amount = amount;
        this.paidAt = paidAt;
        this.note = note;
        this.createdByMemberId = createdByMemberId;
    }

    public Long getId() {
        return id;
    }

    public Long getTeamId() {
        return teamId;
    }

    public Long getFromMemberId() {
        return fromMemberId;
    }

    public Long getToMemberId() {
        return toMemberId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public @Nullable String getNote() {
        return note;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public StoredPayment toRecord() {
        return new StoredPayment(
                id, fromMemberId, toMemberId, amount, paidAt, note, createdByMemberId);
    }
}
