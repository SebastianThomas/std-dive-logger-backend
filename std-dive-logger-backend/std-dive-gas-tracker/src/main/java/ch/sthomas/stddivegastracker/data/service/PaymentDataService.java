package ch.sthomas.stddivegastracker.data.service;

import ch.sthomas.stddivegastracker.data.repository.PaymentRepository;
import ch.sthomas.stddivegastracker.data.repository.TeamMemberRepository;
import ch.sthomas.stddivegastracker.model.entity.PaymentEntity;
import ch.sthomas.stddivegastracker.model.payment.PaymentInput;
import ch.sthomas.stddivegastracker.model.payment.StoredPayment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Payment aggregate (DOMAIN §2, §8). Payments are looked up within the team, so another team's id
 * is a 404; who may call is the service's business.
 */
@Service
public class PaymentDataService {

    private final PaymentRepository paymentRepository;
    private final TeamMemberRepository memberRepository;

    public PaymentDataService(
            final PaymentRepository paymentRepository,
            final TeamMemberRepository memberRepository) {
        this.paymentRepository = paymentRepository;
        this.memberRepository = memberRepository;
    }

    /** Newest first: {@code paidAt}, then id (both descending). */
    @Transactional(readOnly = true)
    public List<StoredPayment> list(final long teamId) {
        return paymentRepository.findByTeamIdOrderByPaidAtDescIdDesc(teamId).stream()
                .map(PaymentEntity::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoredPayment get(final long teamId, final long paymentId) {
        return find(teamId, paymentId).toRecord();
    }

    /**
     * Both parties must belong to this team in any status: a member who left can still settle up
     * (DOMAIN §8). The input guarantees {@code from != to}, so two ids must be found.
     */
    @Transactional
    public StoredPayment create(
            final long teamId, final long createdByMemberId, final PaymentInput input) {
        final var parties = List.of(input.fromMemberId(), input.toMemberId());
        if (memberRepository.countByTeamIdAndIdIn(teamId, parties) != parties.size()) {
            throw new IllegalArgumentException("Unknown member.");
        }
        return paymentRepository
                .save(
                        new PaymentEntity(
                                teamId,
                                input.fromMemberId(),
                                input.toMemberId(),
                                input.amount(),
                                Objects.requireNonNullElseGet(input.paidAt(), Instant::now),
                                input.note(),
                                createdByMemberId))
                .toRecord();
    }

    @Transactional
    public void delete(final long teamId, final long paymentId) {
        paymentRepository.delete(find(teamId, paymentId));
    }

    private PaymentEntity find(final long teamId, final long paymentId) {
        return paymentRepository
                .findByIdAndTeamId(paymentId, teamId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found."));
    }
}
