package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.CertificationDataService;
import ch.sthomas.stddivelogger.model.controller.CertificationBody;
import ch.sthomas.stddivelogger.model.controller.CreateCertificationAgencyBody;
import ch.sthomas.stddivelogger.model.user.Certification;
import ch.sthomas.stddivelogger.model.user.CertificationAgency;
import ch.sthomas.stddivelogger.model.user.User;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CertificationService {

    private final CertificationDataService certificationDataService;

    public CertificationService(final CertificationDataService certificationDataService) {
        this.certificationDataService = certificationDataService;
    }

    public List<CertificationAgency> getAgencies(final String query) {
        if (query.isBlank()) {
            return certificationDataService.findAllAgencies();
        }
        return certificationDataService.findAgenciesByPartialName(query.trim());
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
