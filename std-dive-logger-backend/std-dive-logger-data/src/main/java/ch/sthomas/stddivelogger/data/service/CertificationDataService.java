package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.repository.CertificationAgencyRepository;
import ch.sthomas.stddivelogger.data.repository.CertificationRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.CertificationBody;
import ch.sthomas.stddivelogger.model.controller.CreateCertificationAgencyBody;
import ch.sthomas.stddivelogger.model.entity.CertificationAgencyEntity;
import ch.sthomas.stddivelogger.model.entity.CertificationEntity;
import ch.sthomas.stddivelogger.model.user.Certification;
import ch.sthomas.stddivelogger.model.user.CertificationAgency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class CertificationDataService {

    private final CertificationRepository certificationRepository;
    private final CertificationAgencyRepository certificationAgencyRepository;
    private final UserRepository userRepository;

    public CertificationDataService(
            final CertificationRepository certificationRepository,
            final CertificationAgencyRepository certificationAgencyRepository,
            final UserRepository userRepository) {
        this.certificationRepository = certificationRepository;
        this.certificationAgencyRepository = certificationAgencyRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CertificationAgency> findAllAgencies() {
        return certificationAgencyRepository.findAllByOrderByNameAsc().stream()
                .map(CertificationAgencyEntity::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CertificationAgency> findAgenciesByPartialName(final String query) {
        return certificationAgencyRepository.findByPartialName(query).stream()
                .map(CertificationAgencyEntity::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CertificationAgency> findAllAgenciesOrderedByUserCertCount(final long userId) {
        return certificationAgencyRepository.findAllOrderedByUserCertCount(userId).stream()
                .map(CertificationAgencyEntity::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CertificationAgency> findAgenciesByPartialNameOrderedByUserCertCount(
            final long userId, final String query) {
        return certificationAgencyRepository
                .findByPartialNameOrderedByUserCertCount(userId, query)
                .stream()
                .map(CertificationAgencyEntity::toRecord)
                .toList();
    }

    /**
     * Deliberately the only way to add an agency - no inline "type a new one" shortcut anywhere
     * else, and this rejects anything already present (case-insensitively) rather than silently
     * reusing it, so a near-duplicate typo surfaces as an error instead of quietly creating a
     * second entry. The frontend is expected to put real friction in front of this (search first,
     * an explicit "my agency isn't listed" step) - see CertificationAgencyPicker.vue.
     *
     * <p>Beyond that UI friction, {@link CreateCertificationAgencyBody} itself requires a full name
     * and a real-looking website URL (validated by its own {@code @Pattern}), not just a short code
     * - a bare name is nearly free to type for a troll/duplicate entry, but a plausible full name
     * and URL raise the bar while staying trivial for a genuine, currently- missing agency to
     * provide.
     */
    @Transactional
    public CertificationAgency createAgency(final CreateCertificationAgencyBody body) {
        final var trimmed = body.name().trim();
        if (certificationAgencyRepository.existsByNameIgnoreCase(trimmed)) {
            throw new IllegalArgumentException("An agency named '" + trimmed + "' already exists.");
        }
        return certificationAgencyRepository
                .save(
                        new CertificationAgencyEntity(
                                trimmed,
                                body.fullName().trim(),
                                body.websiteUrl().trim(),
                                body.description() == null ? null : body.description().trim()))
                .toRecord();
    }

    @Transactional(readOnly = true)
    public List<Certification> findForUser(final long userId) {
        return certificationRepository.findByUser_IdOrderByCertDateDesc(userId).stream()
                .map(CertificationEntity::toRecord)
                .toList();
    }

    @Transactional
    public Certification create(final long userId, final CertificationBody body) {
        final var user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        final var agency = findAgencyOrThrow(body.agencyId());
        final var entity = new CertificationEntity(user, agency, body);
        return certificationRepository.save(entity).toRecord();
    }

    @Transactional
    public Certification update(
            final long userId, final long certificationId, final CertificationBody body) {
        final var entity = findOwned(userId, certificationId);
        final var agency = findAgencyOrThrow(body.agencyId());
        entity.update(agency, body);
        return certificationRepository.save(entity).toRecord();
    }

    @Transactional
    public void delete(final long userId, final long certificationId) {
        certificationRepository.delete(findOwned(userId, certificationId));
    }

    private CertificationAgencyEntity findAgencyOrThrow(final long agencyId) {
        return certificationAgencyRepository
                .findById(agencyId)
                .orElseThrow(() -> new NoSuchElementException("Agency not found: " + agencyId));
    }

    private CertificationEntity findOwned(final long userId, final long certificationId) {
        return certificationRepository
                .findByIdAndUser_Id(certificationId, userId)
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Certification "
                                                + certificationId
                                                + " not found or not owned by user "
                                                + userId));
    }
}
