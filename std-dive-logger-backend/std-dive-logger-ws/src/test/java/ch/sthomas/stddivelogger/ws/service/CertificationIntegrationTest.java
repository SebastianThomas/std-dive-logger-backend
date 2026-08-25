package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.CertificationBody;
import ch.sthomas.stddivelogger.model.controller.CreateCertificationAgencyBody;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.service.CertificationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.NoSuchElementException;

/**
 * CRUD + ownership coverage for certifications, and duplicate-rejection coverage for the shared
 * agency lookup list (see V0_4_2__certification.sql's own doc comment for why agencies are a
 * closed, search-first list rather than free text on each certification).
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class CertificationIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                            DockerImageName.parse("postgis/postgis:18-3.6")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withReuse(true);

    @DynamicPropertySource
    static void nonDatasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add("ch.sthomas.stddivelogger.ws.jwt-secret", () -> "test-jwt-secret");
        registry.add(
                "ch.sthomas.stddivelogger.ws.jwt-refresh-secret", () -> "test-jwt-refresh-secret");
        registry.add(
                "ch.sthomas.stddivelogger.storage.r2.base-url", () -> "http://localhost/unused");
        registry.add("ch.sthomas.stddivelogger.storage.r2.bucket", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.storage.r2.account-id", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.storage.r2.access-key", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.storage.r2.secret-key", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.email.address", () -> "test@test.ch");
        registry.add("ch.sthomas.stddivelogger.email.password", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.email.host", () -> "localhost");
    }

    @Autowired private CertificationService certificationService;
    @Autowired private UserRepository userRepository;

    private ch.sthomas.stddivelogger.model.user.User owner;
    private ch.sthomas.stddivelogger.model.user.User other;

    @BeforeEach
    void setUp() {
        owner =
                userRepository
                        .save(new UserEntity("cert-it-owner@test.ch", "hash", "Owner"))
                        .toRecord();
        other =
                userRepository
                        .save(new UserEntity("cert-it-other@test.ch", "hash", "Other"))
                        .toRecord();
    }

    @Test
    void seededAgenciesAreSearchable() {
        final var results = certificationService.getAgencies(owner, "TDI");
        assertThat(results).extracting("name").contains("TDI");
    }

    @Test
    void agenciesTheUserHasMoreCertificationsWithAreRankedFirst() {
        // Alphabetically PADI comes before SSI, so this only passes if the cert-count ranking is
        // actually applied rather than falling back to name order.
        final var padi = certificationService.getAgencies(null, "PADI").getFirst();
        final var ssi = certificationService.getAgencies(null, "SSI").getFirst();

        certificationService.createCertification(
                owner,
                new CertificationBody(
                        ssi.id(),
                        "Open Water",
                        LocalDate.of(2020, 1, 1),
                        null,
                        null,
                        null,
                        null,
                        null));
        certificationService.createCertification(
                owner,
                new CertificationBody(
                        ssi.id(),
                        "Advanced",
                        LocalDate.of(2021, 1, 1),
                        null,
                        null,
                        null,
                        null,
                        null));

        final var rankedForOwner = certificationService.getAgencies(owner, "");
        assertThat(rankedForOwner).extracting("id").containsSubsequence(ssi.id(), padi.id());

        // A user with no certifications at all still sees the plain alphabetical order.
        final var rankedForOther = certificationService.getAgencies(other, "");
        assertThat(rankedForOther).extracting("id").containsSubsequence(padi.id(), ssi.id());

        // Ties (nobody has any certs) fall back to alphabetical, same as an unauthenticated call.
        final var unauthenticated = certificationService.getAgencies(null, "");
        assertThat(unauthenticated).extracting("id").containsSubsequence(padi.id(), ssi.id());
    }

    @Test
    void creatingADuplicateAgencyNameIsRejected() {
        assertThatThrownBy(
                        () ->
                                certificationService.createAgency(
                                        new CreateCertificationAgencyBody(
                                                "tdi",
                                                "Technical Diving International",
                                                "https://www.tdisdi.com",
                                                null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void creatingAnAgencyRequiresFullNameAndUrl() {
        final var created =
                certificationService.createAgency(
                        new CreateCertificationAgencyBody(
                                "TESTAGY",
                                "Test Agency International",
                                "https://test-agency.example.com",
                                "A fictional agency used only in tests."));
        assertThat(created.name()).isEqualTo("TESTAGY");
        assertThat(created.fullName()).isEqualTo("Test Agency International");
        assertThat(created.websiteUrl()).isEqualTo("https://test-agency.example.com");
    }

    @Test
    void crudRoundTripAndOwnershipScoping() {
        final var tdi = certificationService.getAgencies(owner, "TDI").getFirst();

        final var created =
                certificationService.createCertification(
                        owner,
                        new CertificationBody(
                                tdi.id(),
                                "Open Water Diver",
                                LocalDate.of(2020, 6, 1),
                                "OW-12345",
                                "Jane Instructor",
                                "Dive Shop Zurich",
                                null,
                                null));
        assertThat(created.agency().name()).isEqualTo("TDI");
        assertThat(created.level()).isEqualTo("Open Water Diver");

        assertThat(certificationService.getCertificationsForUser(owner))
                .extracting("id")
                .containsExactly(created.id());
        assertThat(certificationService.getCertificationsForUser(other)).isEmpty();

        // Ownership: another user can't update or delete it.
        final var otherUsersEdit =
                new CertificationBody(
                        tdi.id(),
                        "Advanced",
                        LocalDate.of(2021, 1, 1),
                        null,
                        null,
                        null,
                        null,
                        null);
        assertThatThrownBy(
                        () ->
                                certificationService.updateCertification(
                                        other, created.id(), otherUsersEdit))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> certificationService.deleteCertification(other, created.id()))
                .isInstanceOf(NoSuchElementException.class);

        final var updated =
                certificationService.updateCertification(
                        owner,
                        created.id(),
                        new CertificationBody(
                                tdi.id(),
                                "Advanced Open Water",
                                LocalDate.of(2021, 3, 1),
                                null,
                                null,
                                null,
                                null,
                                null));
        assertThat(updated.level()).isEqualTo("Advanced Open Water");

        certificationService.deleteCertification(owner, created.id());
        assertThat(certificationService.getCertificationsForUser(owner)).isEmpty();
    }
}
