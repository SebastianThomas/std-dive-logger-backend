package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.CertificationDataService;
import ch.sthomas.stddivelogger.model.controller.CertificationBody;
import ch.sthomas.stddivelogger.model.controller.CreateCertificationAgencyBody;
import ch.sthomas.stddivelogger.model.user.Certification;
import ch.sthomas.stddivelogger.model.user.CertificationAgency;
import ch.sthomas.stddivelogger.model.user.User;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CertificationService {

    private final CertificationDataService certificationDataService;

    public CertificationService(final CertificationDataService certificationDataService) {
        this.certificationDataService = certificationDataService;
    }

    // Agencies the current user already has more certifications with are ranked first, so a
    // likely match surfaces before typing anything - falls back to plain alphabetical order for
    // an unauthenticated caller.
    public List<CertificationAgency> getAgencies(final @Nullable User user, final String query) {
        if (user == null) {
            return query.isBlank()
                    ? certificationDataService.findAllAgencies()
                    : certificationDataService.findAgenciesByPartialName(query.trim());
        }
        return query.isBlank()
                ? certificationDataService.findAllAgenciesOrderedByUserCertCount(user.id())
                : certificationDataService.findAgenciesByPartialNameOrderedByUserCertCount(
                        user.id(), query.trim());
    }

    public CertificationAgency createAgency(final CreateCertificationAgencyBody body) {
        return certificationDataService.createAgency(body);
    }

    public List<Certification> getCertificationsForUser(final User user) {
        return certificationDataService.findForUser(user.id());
    }

    public Certification createCertification(final User user, final CertificationBody body) {
        return certificationDataService.create(user.id(), body);
    }

    public Certification updateCertification(
            final User user, final long certificationId, final CertificationBody body) {
        return certificationDataService.update(user.id(), certificationId, body);
    }

    public void deleteCertification(final User user, final long certificationId) {
        certificationDataService.delete(user.id(), certificationId);
    }
}
