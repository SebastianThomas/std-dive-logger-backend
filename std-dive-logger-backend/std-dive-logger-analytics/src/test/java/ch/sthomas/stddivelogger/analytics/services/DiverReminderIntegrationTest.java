package ch.sthomas.stddivelogger.analytics.services;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveComputerManufacturerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveComputerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.PushSubscriptionRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.DiverReminderDataService;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.Suit;
import ch.sthomas.stddivelogger.model.dive.home.ReminderKind;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.entity.DiveComputerEntity;
import ch.sthomas.stddivelogger.model.entity.DiveComputerManufacturerEntity;
import ch.sthomas.stddivelogger.model.entity.DiveEntity;
import ch.sthomas.stddivelogger.model.entity.DiveMeasurementEntity;
import ch.sthomas.stddivelogger.model.entity.DiveProfileEntity;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.PushSubscriptionEntity;
import ch.sthomas.stddivelogger.model.entity.SuitEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;

import jakarta.persistence.EntityManager;

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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * End-to-end for the stored home-page reminders: a dive anniversary surfaces on its date, an
 * overdue diver gets a "dive again" nudge tuned to their own cadence, dismissing sticks across a
 * recompute, and the push queue is drained by {@code markPushed}.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiverReminderIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                            DockerImageName.parse("postgis/postgis:18-3.6")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withReuse(true);

    @DynamicPropertySource
    static void nonDatasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add(
                "ch.sthomas.stddivelogger.storage.r2.base-url", () -> "http://localhost/unused");
    }

    @Autowired private EntityManager entityManager;
    @Autowired private DiverReminderDataService reminders;
    @Autowired private PushSubscriptionRepository pushSubscriptions;
    @Autowired private UserRepository userRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;
    @Autowired private DiveRepository diveRepository;
    @Autowired private DiveComputerRepository diveComputerRepository;
    @Autowired private DiveComputerManufacturerRepository diveComputerManufacturerRepository;

    private UserEntity user;
    private DiveComputerEntity computer;
    private DiveSiteEntity site;
    private int nextNumber = 1;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new UserEntity("reminder-it@test.ch", "hash", "ReminderIT"));
        final var man =
                diveComputerManufacturerRepository.save(
                        new DiveComputerManufacturerEntity("Reminder IT Man"));
        computer =
                diveComputerRepository.save(
                        new DiveComputerEntity(null, "REMINDER-IT-COMPUTER", man, user));
        site =
                diveSiteRepository.save(
                        new DiveSiteEntity("Blue Hole", new Location(28.57, 34.53).toPoint()));
    }

    private void dive(final Instant start, final double maxDepth) {
        final var m0 =
                new DiveMeasurementEntity(
                        new DiveMeasurement(
                                start, null, maxDepth, null, List.of(), null, null, null, null,
                                null, null, null, null),
                        null);
        final var m1 =
                new DiveMeasurementEntity(
                        new DiveMeasurement(
                                start.plusSeconds(2700),
                                null,
                                maxDepth,
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
                new DiveProfileEntity(computer, start, start.plusSeconds(2700), List.of(m0, m1));
        final var suit = new SuitEntity(user, Suit.createUnknown(user.toRecord()));
        diveRepository.save(
                new DiveEntity(
                        nextNumber++,
                        "reminder-it",
                        "",
                        Visibility.EMPTY,
                        DiveGasConsumption.EMPTY,
                        suit,
                        null,
                        null,
                        DiveConfiguration.createEmpty(user.toRecord()),
                        user,
                        site,
                        List.of(profile),
                        List.of(),
                        cs -> {
                            throw new UnsupportedOperationException("no cylinders");
                        }));
    }

    private static Instant daysAgo(final long d) {
        return Instant.now().minus(d, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    /** Same calendar month + day as today, {@code years} back (Feb-29 lands on the 28th - rare). */
    private static Instant yearsAgoToday(final int years) {
        return LocalDate.now(ZoneOffset.UTC)
                .minusYears(years)
                .atStartOfDay(ZoneOffset.UTC)
                .plusHours(9)
                .toInstant();
    }

    @Test
    void surfacesADiveAnniversaryOnItsDate() {
        dive(yearsAgoToday(3), 27.0);
        dive(daysAgo(400), 18.0);
        dive(daysAgo(30), 12.0);

        reminders.computeAndStore(user.getId());
        entityManager.flush();

        final var active = reminders.getActiveReminders(user.getId());
        final var anniversary =
                active.stream()
                        .filter(r -> r.kind() == ReminderKind.DIVE_ANNIVERSARY)
                        .findFirst()
                        .orElseThrow();
        assertThat(anniversary.yearsAgo()).isEqualTo(3);
        assertThat(anniversary.title()).contains("3 years ago");
        assertThat(anniversary.body()).contains("Blue Hole");
        assertThat(anniversary.diveId()).isNotNull();
    }

    @Test
    void nudgesAnOverdueDiverAndComputesLazilyOnRead() {
        // ~every 14 days for the last ~7 months, then nothing for ~9 weeks
        for (long d = 210; d >= 63; d -= 14) {
            dive(daysAgo(d), 20.0);
        }
        entityManager.flush();

        // getActiveReminders should compute on first read (no prior computeAndStore)
        final var active = reminders.getActiveReminders(user.getId());
        final var nudge =
                active.stream()
                        .filter(r -> r.kind() == ReminderKind.DIVE_AGAIN_NUDGE)
                        .findFirst()
                        .orElseThrow();
        assertThat(nudge.body()).contains("weeks since your last dive");
        assertThat(nudge.diveId()).isNull();
    }

    @Test
    void noNudgeForADiverStillInTheirRhythm() {
        for (long d = 140; d >= 7; d -= 14) {
            dive(daysAgo(d), 20.0);
        }
        entityManager.flush();

        final var active = reminders.getActiveReminders(user.getId());
        assertThat(active.stream().anyMatch(r -> r.kind() == ReminderKind.DIVE_AGAIN_NUDGE))
                .isFalse();
    }

    @Test
    void dismissingAReminderRemovesItAndSurvivesRecompute() {
        dive(yearsAgoToday(2), 22.0);
        dive(daysAgo(20), 15.0);
        reminders.computeAndStore(user.getId());
        entityManager.flush();

        final var anniversary =
                reminders.getActiveReminders(user.getId()).stream()
                        .filter(r -> r.kind() == ReminderKind.DIVE_ANNIVERSARY)
                        .findFirst()
                        .orElseThrow();

        assertThat(reminders.dismiss(user.getId(), anniversary.id())).isTrue();
        entityManager.flush();
        assertThat(reminders.getActiveReminders(user.getId())).isEmpty();

        // a re-run must not resurrect it
        reminders.computeAndStore(user.getId());
        entityManager.flush();
        assertThat(reminders.getActiveReminders(user.getId())).isEmpty();

        // and a different user's dismiss can't touch it
        assertThat(reminders.dismiss(user.getId() + 999, anniversary.id())).isFalse();
    }

    @Test
    void queuesPushesForSubscribedDiversAndDrainsThemOnMarkPushed() {
        dive(yearsAgoToday(4), 30.0);
        dive(daysAgo(10), 12.0);
        pushSubscriptions.save(
                new PushSubscriptionEntity(
                        user.getId(), "https://push.example/abc", "p256dh-key", "auth-key", "UA"));
        reminders.computeAndStore(user.getId());
        entityManager.flush();

        final var due = reminders.findDuePushes();
        assertThat(due).anyMatch(p -> p.kind() == ReminderKind.DIVE_ANNIVERSARY);
        due.forEach(p -> reminders.markPushed(p.reminderId()));
        entityManager.flush();

        assertThat(reminders.findDuePushes()).isEmpty();
    }
}
