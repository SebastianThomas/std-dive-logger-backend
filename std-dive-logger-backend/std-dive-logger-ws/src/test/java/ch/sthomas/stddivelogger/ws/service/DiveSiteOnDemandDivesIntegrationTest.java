package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveComputerManufacturerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveComputerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.SuitRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.entity.DiveComputerEntity;
import ch.sthomas.stddivelogger.model.entity.DiveComputerManufacturerEntity;
import ch.sthomas.stddivelogger.model.entity.DiveEntity;
import ch.sthomas.stddivelogger.model.entity.DiveMeasurementEntity;
import ch.sthomas.stddivelogger.model.entity.DiveProfileEntity;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.SuitEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;

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

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Regression coverage for {@code DiveDataService#findDivesAtSiteForUser} - the on-demand, per-site
 * dive list backing {@code GET /v1/dives/sites/{id}/dives}, added alongside {@code
 * DiveService#getSitesByUser} no longer inlining every site's dive list once a user has too many
 * sites. Since this is a new access-controlled surface, this specifically checks it can't leak a
 * dive at a shared site to a user who isn't its owner and hasn't been granted read access.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveSiteOnDemandDivesIntegrationTest {

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

    @Autowired private DiveDataService diveDataService;
    @Autowired private UserRepository userRepository;
    @Autowired private SuitRepository suitRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;
    @Autowired private DiveRepository diveRepository;
    @Autowired private DiveComputerRepository diveComputerRepository;
    @Autowired private DiveComputerManufacturerRepository diveComputerManufacturerRepository;

    private UserEntity owner;
    private UserEntity otherUser;
    private long siteId;
    private long diveId;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new UserEntity("site-dives-owner@test.ch", "hash", "Owner"));
        otherUser =
                userRepository.save(new UserEntity("site-dives-other@test.ch", "hash", "Other"));
        final var suit =
                suitRepository.save(
                        new SuitEntity(
                                owner,
                                ch.sthomas.stddivelogger.model.dive.gear.Suit.createUnknown(
                                        owner.toRecord())));
        final var site =
                diveSiteRepository.save(
                        new DiveSiteEntity(
                                "On-Demand Dives Test Site", new Location(47.0, 8.0).toPoint()));
        siteId = site.toRecord().id();
        final var manufacturer =
                diveComputerManufacturerRepository.save(
                        new DiveComputerManufacturerEntity("Test Manufacturer"));
        final var computer =
                diveComputerRepository.save(
                        new DiveComputerEntity(
                                null, "SITE-DIVES-TEST-COMPUTER", manufacturer, owner));
        final var start = Instant.parse("2026-06-01T10:00:00Z");
        final var m0 =
                new DiveMeasurementEntity(
                        new DiveMeasurement(
                                start, null, 10.0, null, List.of(), null, null, null, null, null,
                                null, null, null),
                        null);
        final var m1 =
                new DiveMeasurementEntity(
                        new DiveMeasurement(
                                start.plusSeconds(60),
                                null,
                                12.0,
                                null,
                                List.of(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null),
                        null);
        final var profile =
                new DiveProfileEntity(computer, start, start.plusSeconds(60), List.of(m0, m1));
        final var dive =
                new DiveEntity(
                        1,
                        "site-dives-test-dive",
                        "",
                        Visibility.EMPTY,
                        DiveGasConsumption.EMPTY,
                        suit,
                        null,
                        null,
                        DiveConfiguration.createEmpty(owner.toRecord()),
                        owner,
                        site,
                        List.of(profile),
                        List.of(),
                        cs -> {
                            throw new UnsupportedOperationException("no cylinders in this fixture");
                        });
        diveId = diveRepository.save(dive).getId();
    }

    @Test
    void ownerSeesTheirOwnDiveAtTheSite() {
        final var dives = diveDataService.findDivesAtSiteForUser(owner.getId(), siteId, true);

        assertThat(dives).extracting(i -> i.id()).containsExactly(diveId);
    }

    @Test
    void nonOwnerWithoutReadAccessSeesNothingAtTheSiteEvenWithReaderInclusiveLookup() {
        final var ownOnly = diveDataService.findDivesAtSiteForUser(otherUser.getId(), siteId, true);
        final var readerInclusive =
                diveDataService.findDivesAtSiteForUser(otherUser.getId(), siteId, false);

        assertThat(ownOnly).isEmpty();
        assertThat(readerInclusive).isEmpty();
    }

    @Test
    void nonOwnerWithGrantedReadAccessSeesTheDiveOnlyViaReaderInclusiveLookup() {
        diveDataService.saveReaders(diveId, Set.of(otherUser.getId()));

        final var ownOnly = diveDataService.findDivesAtSiteForUser(otherUser.getId(), siteId, true);
        final var readerInclusive =
                diveDataService.findDivesAtSiteForUser(otherUser.getId(), siteId, false);

        assertThat(ownOnly).isEmpty();
        assertThat(readerInclusive).extracting(i -> i.id()).containsExactly(diveId);
    }
}
