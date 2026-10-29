package ch.sthomas.stddivegastracker.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.support.AbstractPaymentIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

import java.time.Instant;
import java.util.Map;

/**
 * DOMAIN §5.6 / §6: every fill and reading of a bank lies in its service window {@code [openedAt,
 * closedAt)}, so a bank closes after its opening and after its last fill and reading, and a reading
 * outside the window is refused.
 */
final class VesselServiceWindowIntegrationTest extends AbstractPaymentIntegrationTest {

    @Test
    void aBankClosesOnlyAfterItsOpeningAndItsLastFillAndReading() {
        final var crew = crew(); // bank opened at T0
        fillFor(crew, crew.b().memberId(), 80); // day 1
        reading(crew, day(3)).expectStatus().isOk();

        assertThat(badRequestDetail(close(crew, T0)))
                .isEqualTo("A bank can only be closed after it was opened.");
        assertThat(badRequestDetail(close(crew, day(1))))
                .isEqualTo("A bank can only be closed after its last fill and reading.");
        assertThat(badRequestDetail(close(crew, day(2))))
                .isEqualTo("A bank can only be closed after its last fill and reading.");

        close(crew, day(4)).expectStatus().isOk();
        assertThat(getVessel(crew.adminEmail(), crew.teamId(), crew.bank().vesselId()).closedAt())
                .isEqualTo(day(4));
    }

    @Test
    void aVoidedFillDoesNotHoldTheBankOpen() {
        final var crew = crew();
        final var fill = fillFor(crew, crew.b().memberId(), 80); // day 1
        voidFill(crew.adminEmail(), crew.teamId(), fill.fillId(), "Recorded twice");

        close(crew, day(1)).expectStatus().isOk();
    }

    @Test
    void aReadingLiesInsideTheServiceWindow() {
        final var crew = crew();

        assertThat(badRequestDetail(reading(crew, T0.minusSeconds(60))))
                .isEqualTo("That bank was not in service at that time.");
        reading(crew, T0).expectStatus().isOk();

        close(crew, day(4)).expectStatus().isOk();
        // backdated into the window is fine; after the close (or now) it is not
        reading(crew, day(3)).expectStatus().isOk();
        assertThat(badRequestDetail(reading(crew, day(4)))).isEqualTo("This bank is closed.");
        assertThat(
                        badRequestDetail(
                                send(
                                        HttpMethod.POST,
                                        bankUri(crew) + "/readings",
                                        crew.adminEmail(),
                                        Map.of("pressureBar", 20))))
                .isEqualTo("This bank is closed.");
    }

    private ResponseSpec close(final Crew crew, final Instant closedAt) {
        return send(
                HttpMethod.POST,
                bankUri(crew) + "/close",
                crew.adminEmail(),
                Map.of("closingPressureBar", 25, "closedAt", closedAt.toEpochMilli()));
    }

    private ResponseSpec reading(final Crew crew, final Instant measuredAt) {
        return send(
                HttpMethod.POST,
                bankUri(crew) + "/readings",
                crew.adminEmail(),
                Map.of("pressureBar", 150, "measuredAt", measuredAt.toEpochMilli()));
    }

    private static String bankUri(final Crew crew) {
        return vesselsUri(crew.teamId()) + "/" + crew.bank().vesselId();
    }
}
