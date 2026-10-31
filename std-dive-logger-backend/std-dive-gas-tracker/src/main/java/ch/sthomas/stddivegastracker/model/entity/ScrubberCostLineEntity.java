package ch.sthomas.stddivegastracker.model.entity;

import ch.sthomas.stddivegastracker.model.scrubber.StoredCanister;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

/** Money paid for a canister by a member (DOMAIN §12); wrong ones are deleted and re-added. */
@Entity
@Table(name = "t_scrubber_cost_line")
@SuppressWarnings("NullAway.Init")
public class ScrubberCostLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_scrubber_cost_line_id", nullable = false)
    private Long id;

    @Column(name = "fk_payer_member_id", nullable = false)
    private Long payerMemberId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(name = "note", length = 200)
    private @Nullable String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ScrubberCostLineEntity() {}

    public ScrubberCostLineEntity(
            final Long payerMemberId,
            final BigDecimal amount,
            final Instant paidAt,
            final @Nullable String note) {
        this.payerMemberId = payerMemberId;
        this.amount = amount;
        this.paidAt = paidAt;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public StoredCanister.CostLine toRecord() {
        return new StoredCanister.CostLine(id, payerMemberId, amount, paidAt, note);
    }
}
