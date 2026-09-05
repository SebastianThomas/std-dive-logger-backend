package ch.sthomas.stddivelogger.analytics.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ch.sthomas.stddivelogger.data.repository.DiveComputerManufacturerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveComputerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.DiveSiteStatsDataService;
import ch.sthomas.stddivelogger.data.service.DiveSiteSuggestionDataService;
import ch.sthomas.stddivelogger.model.dive.DiveSiteSuggestion;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.Suit;
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
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;

/**
 * End-to-end for "suggest a dive site": {@link DiveSiteStatsDataService#refreshAll()} bulk-writes
 * the global per-site aggregates, then {@link DiveSiteSuggestionDataService#suggest} scores every
 * site with data against one diver's own history and location.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveSiteSuggestionIntegrationTest {

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
    @Autowired private DiveSiteStatsDataService statsService;
    @Autowired private DiveSiteSuggestionDataService suggestionService;
    @Autowired private UserRepository userRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;
    @Autowired private DiveRepository diveRepository;
    @Autowired private DiveComputerRepository diveComputerRepository;
    @Autowired private DiveComputerManufacturerRepository diveComputerManufacturerRepository;

    private UserEntity user;
    private UserEntity otherDiver;
    private DiveComputerManufacturerEntity manufacturer;
    private int nextNumber = 1;
    private int nextSerial = 1;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new UserEntity("suggest-it@test.ch", "hash", "SuggestIT"));
        otherDiver =
                userRepository.save(new UserEntity("suggest-it-2@test.ch", "hash", "SuggestIT2"));
        manufacturer =
                diveComputerManufacturerRepository.save(
                        new DiveComputerManufacturerEntity("Suggest IT Man"));
    }

    private DiveSiteEntity site(final String name, final double lat, final double lon) {
        return diveSiteRepository.save(new DiveSiteEntity(name, new Location(lat, lon).toPoint()));
    }

    private void dive(
            final UserEntity diver,
            final DiveSiteEntity diveSite,
            final Instant start,
            final double maxDepth,
            final Visibility visibility) {
        final var computer =
                diveComputerRepository.save(
                        new DiveComputerEntity(
                                null, "SUGGEST-IT-" + nextSerial++, manufacturer, diver));
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
        final var suit = new SuitEntity(diver, Suit.createUnknown(diver.toRecord()));
        diveRepository.save(
                new DiveEntity(
                        nextNumber++,
                        "suggest-it",
                        "",
                        visibility,
                        DiveGasConsumption.EMPTY,
                        suit,
                        null,
                        null,
                        DiveConfiguration.createEmpty(diver.toRecord()),
                        diver,
                        diveSite,
                        List.of(profile),
                        List.of(),
                        cs -> {
                            throw new UnsupportedOperationException("no cylinders");
                        }));
    }

    private static Instant daysAgo(final long d) {
        return Instant.now().minus(d, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    private List<DiveSiteSuggestion> suggest() {
        entityManager.flush();
        statsService.refreshAll();
        return suggestionService.suggest(user.getId(), null, null, null, 20);
    }

    private static DiveSiteSuggestion find(final List<DiveSiteSuggestion> all, final String name) {
        return all.stream().filter(s -> s.site().name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void surfacesASiteTheDiverHasNotVisitedInAWhileAsDueForARevisit() {
        final var revisitReef = site("Revisit Reef", 1.0, 1.0);
        dive(user, revisitReef, daysAgo(200), 15.0, Visibility.EMPTY);
        dive(user, revisitReef, daysAgo(60), 15.0, Visibility.EMPTY);

        final var suggestion = find(suggest(), "Revisit Reef");

        assertThat(suggestion.daysSinceLastVisit()).isEqualTo(60);
        assertThat(suggestion.reasons())
                .anyMatch(r -> r.contains("due for a revisit") && r.contains("2 times before"));
    }

    @Test
    void excludesASiteVisitedWithinTheLastThreeWeeksEvenIfOtherwiseNoteworthy() {
        final var tooFresh = site("Too Fresh", 2.0, 2.0);
        dive(user, tooFresh, daysAgo(5), 15.0, Visibility.EMPTY);
        dive(otherDiver, tooFresh, daysAgo(3), 15.0, Visibility.EMPTY);
        final var thirdDiver =
                userRepository.save(new UserEntity("suggest-it-3@test.ch", "hash", "SuggestIT3"));
        dive(thirdDiver, tooFresh, daysAgo(2), 15.0, Visibility.EMPTY);

        assertThat(suggest()).noneMatch(s -> s.site().name().equals("Too Fresh"));
    }

    @Test
    void favorsASiteWithBetterVisibilityThanItsNeighbours() {
        final var clearCove = site("Clear Cove", 10.0, 10.0);
        final var murkyBay = site("Murky Bay", 10.05, 10.05);
        dive(otherDiver, clearCove, daysAgo(10), 18.0, new Visibility(25.0, "", null));
        dive(otherDiver, murkyBay, daysAgo(10), 18.0, new Visibility(6.0, "", null));

        final var suggestion = find(suggest(), "Clear Cove");

        assertThat(suggestion.avgVisibilityM()).isEqualTo(25.0);
        assertThat(suggestion.reasons())
                .anyMatch(r -> r.contains("Visibility here averages") && r.contains("better than"));
    }

    @Test
    void highlightsASiteThatIsPopularWithOtherDiversRecently() {
        final var busyWreck = site("Busy Wreck", 3.0, 3.0);
        final var thirdDiver =
                userRepository.save(new UserEntity("suggest-it-4@test.ch", "hash", "SuggestIT4"));
        dive(otherDiver, busyWreck, daysAgo(5), 20.0, Visibility.EMPTY);
        dive(thirdDiver, busyWreck, daysAgo(8), 20.0, Visibility.EMPTY);

        final var suggestion = find(suggest(), "Busy Wreck");

        assertThat(suggestion.recentDiverCount30d()).isEqualTo(2);
        assertThat(suggestion.reasons())
                .anyMatch(r -> r.contains("2 other divers logged dives here in the last 30 days"));
    }

    @Test
    void surfacesALowVolumeSiteWithGoodConditionsAsAnUnderratedPick() {
        final var hiddenGem = site("Hidden Gem", 4.0, 4.0);
        dive(otherDiver, hiddenGem, daysAgo(100), 22.0, new Visibility(20.0, "", null));

        final var suggestion = find(suggest(), "Hidden Gem");

        assertThat(suggestion.totalDives()).isEqualTo(1);
        assertThat(suggestion.reasons())
                .anyMatch(r -> r.contains("Only 1 dive logged here") && r.contains("underrated"));
    }

    @Test
    void warnsWhenASiteIsNotablyDeeperThanTheDiversOwnLoggedMax() {
        final var warmup = site("Warmup Site", 5.0, 5.0);
        dive(user, warmup, daysAgo(300), 12.0, Visibility.EMPTY);
        final var deepTrench = site("Deep Trench", 6.0, 6.0);
        dive(otherDiver, deepTrench, daysAgo(50), 55.0, Visibility.EMPTY);

        final var suggestion = find(suggest(), "Deep Trench");

        assertThat(suggestion.reasons())
                .anyMatch(
                        r ->
                                r.contains("notably deeper than your own logged max")
                                        && r.contains("55"));
    }

    @Test
    void appliesAMildDepthCautionWhenNeitherComfortableNorClearlyMismatched() {
        final var warmup = site("Warmup Site", 50.0, 50.0);
        dive(user, warmup, daysAgo(300), 20.0, Visibility.EMPTY);
        final var cautiousCove = site("Cautious Cove", 51.0, 51.0);
        dive(otherDiver, cautiousCove, daysAgo(60), 27.0, new Visibility(20.0, "", null));

        final var suggestion = find(suggest(), "Cautious Cove");

        assertThat(suggestion.score()).isEqualTo(2.0);
    }

    @Test
    void mentionsProximityWhenTheDiversLocationIsGiven() {
        final var closeCall = site("Close Call", 20.0, 20.0);
        dive(otherDiver, closeCall, daysAgo(100), 18.0, Visibility.EMPTY);

        entityManager.flush();
        statsService.refreshAll();
        final var all = suggestionService.suggest(user.getId(), 20.01, 20.01, null, 20);
        final var suggestion = find(all, "Close Call");

        assertThat(suggestion.distanceKm()).isLessThan(5.0);
        assertThat(suggestion.reasons()).anyMatch(r -> r.contains("from your current location"));
    }

    @Test
    void appliesOnlyAMildPenaltyForBeingModestlyBeyondThePreferredDistance() {
        final var near = site("Near Spot", 0.045, 0.0);
        final var farIsh = site("Far-ish Spot", 0.2515, 0.0);
        dive(otherDiver, near, daysAgo(100), 22.0, new Visibility(20.0, "", null));
        dive(otherDiver, farIsh, daysAgo(100), 22.0, new Visibility(20.0, "", null));

        entityManager.flush();
        statsService.refreshAll();
        final var all = suggestionService.suggest(user.getId(), 0.0, 0.0, 20.0, 20);
        final var nearSuggestion = find(all, "Near Spot");
        final var farSuggestion = find(all, "Far-ish Spot");

        assertThat(nearSuggestion.distanceKm()).isCloseTo(5.0, within(2.0));
        assertThat(farSuggestion.distanceKm()).isCloseTo(28.0, within(2.0));
        assertThat(nearSuggestion.reasons())
                .anyMatch(r -> r.contains("from your current location"));
        assertThat(farSuggestion.reasons())
                .noneMatch(r -> r.contains("from your current location"));
        assertThat(nearSuggestion.score()).isGreaterThan(farSuggestion.score());
        assertThat(farSuggestion.score()).isPositive();
    }

    @Test
    void marksBothSitesAsTopPicksWhenScoresAreNearlyTied() {
        final var twinA = site("Twin A", 40.0, 40.0);
        final var twinB = site("Twin B", 41.0, 41.0);
        dive(otherDiver, twinA, daysAgo(100), 22.0, new Visibility(20.0, "", null));
        dive(otherDiver, twinB, daysAgo(100), 22.0, new Visibility(20.0, "", null));

        final var all = suggest();

        assertThat(find(all, "Twin A").topPick()).isTrue();
        assertThat(find(all, "Twin B").topPick()).isTrue();
    }

    @Test
    void marksOnlyTheClearWinnerAsATopPickWhenScoresAreFarApart() {
        final var revisitReef = site("Revisit Reef", 1.0, 1.0);
        dive(user, revisitReef, daysAgo(400), 15.0, Visibility.EMPTY);
        dive(otherDiver, revisitReef, daysAgo(5), 15.0, new Visibility(25.0, "", null));
        final var thirdDiver =
                userRepository.save(new UserEntity("suggest-it-5@test.ch", "hash", "SuggestIT5"));
        dive(thirdDiver, revisitReef, daysAgo(8), 15.0, Visibility.EMPTY);

        final var deepTrench = site("Deep Trench", 6.0, 6.0);
        dive(otherDiver, deepTrench, daysAgo(50), 55.0, Visibility.EMPTY);
        final var warmup = site("Warmup Site", 5.0, 5.0);
        dive(user, warmup, daysAgo(300), 12.0, Visibility.EMPTY);

        final var all = suggest();

        assertThat(find(all, "Revisit Reef").topPick()).isTrue();
        assertThat(find(all, "Deep Trench").topPick()).isFalse();
    }

    @Test
    void returnsANonDeterministicNumberOfAdditionalSuggestionsWithinBounds() {
        final var winner = site("Big Winner", 1.0, 1.0);
        dive(user, winner, daysAgo(400), 15.0, Visibility.EMPTY);
        dive(otherDiver, winner, daysAgo(5), 15.0, new Visibility(25.0, "", null));
        final var thirdDiver =
                userRepository.save(new UserEntity("suggest-it-6@test.ch", "hash", "SuggestIT6"));
        dive(thirdDiver, winner, daysAgo(8), 15.0, Visibility.EMPTY);

        for (int i = 0; i < 8; i++) {
            final var extra = site("Extra " + i, 70.0 + i, 70.0 + i);
            dive(otherDiver, extra, daysAgo(100), 22.0, new Visibility(20.0, "", null));
        }
        entityManager.flush();
        statsService.refreshAll();

        final var sizes = new HashSet<Integer>();
        for (int i = 0; i < 30; i++) {
            final var all = suggestionService.suggest(user.getId(), null, null, null, 20);
            assertThat(all.getFirst().site().name()).isEqualTo("Big Winner");
            assertThat(all.getFirst().topPick()).isTrue();
            sizes.add(all.size());
        }

        assertThat(sizes).allMatch(s -> s >= 4 && s <= 8);
        assertThat(sizes.size()).isGreaterThan(1);
    }
}
