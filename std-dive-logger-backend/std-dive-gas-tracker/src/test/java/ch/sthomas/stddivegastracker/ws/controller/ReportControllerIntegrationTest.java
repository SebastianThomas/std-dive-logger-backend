package ch.sthomas.stddivegastracker.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ch.sthomas.stddivegastracker.model.fill.Fill;
import ch.sthomas.stddivegastracker.model.fill.StageResult;
import ch.sthomas.stddivegastracker.model.report.ConsumptionReport;
import ch.sthomas.stddivegastracker.model.report.LossesReport;
import ch.sthomas.stddivegastracker.model.report.PolicyComparison;
import ch.sthomas.stddivegastracker.support.AbstractPaymentIntegrationTest;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** API.md §6 reports through HTTP, cross-checked against the balances (DOMAIN §7.6, §8). */
final class ReportControllerIntegrationTest extends AbstractPaymentIntegrationTest {

    @Test
    void consumptionCountsCylinderFillsAndBoughtGasAtTodaysPrices() {
        final var crew = crew();
        final var b1 = fillFor(crew, crew.b().memberId(), 80);
        final var b2 = fillFor(crew, crew.b().memberId(), 50);
        final var c1 = fillFor(crew, crew.c().memberId(), 30);
        // AIR only: a fill that counts, but takes no bought gas
        createFill(
                crew.adminEmail(),
                crew.teamId(),
                at(fillBody(cylinder(12, crew.c().memberId()), 0, fromAir(200)), day(1)));
        final var voided = fillFor(crew, crew.b().memberId(), 20);
        voidFill(crew.adminEmail(), crew.teamId(), voided.fillId(), "Recorded twice");

        final var report = consumption(crew.adminEmail(), crew.teamId(), null, null);
        final var balances = balancesOf(crew.adminEmail(), crew.teamId());

        assertThat(report.rows()).hasSize(2);
        final var b = report.rows().getFirst();
        final var c = report.rows().get(1);
        assertThat(b.member().memberId()).isEqualTo(crew.b().memberId());
        assertThat(b.fills()).isEqualTo(2);
        assertThat(b.o2Litres()).isCloseTo(o2Of(b1) + o2Of(b2), within(1e-6));
        assertThat(b.heLitres()).isZero();
        assertThat(b.charge()).isEqualTo(balanceOf(balances, crew.b().memberId()).fillCharges());
        assertThat(c.member().memberId()).isEqualTo(crew.c().memberId());
        assertThat(c.fills()).isEqualTo(2);
        assertThat(c.o2Litres()).isCloseTo(o2Of(c1), within(1e-6));
        assertThat(c.charge()).isEqualTo(balanceOf(balances, crew.c().memberId()).fillCharges());
    }

    @Test
    void consumptionOnlyCountsFillsInsideThePeriod() {
        final var crew = crew();
        fillFor(crew, crew.b().memberId(), 50); // day 1
        final var later =
                createFill(
                        crew.adminEmail(),
                        crew.teamId(),
                        at(
                                fillBody(
                                        cylinder(12, crew.b().memberId()),
                                        0,
                                        fromBank(crew.bank().vesselId(), 40)),
                                day(5)));

        final var report = consumption(crew.adminEmail(), crew.teamId(), day(2), day(10));

        assertThat(report.rows())
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.fills()).isEqualTo(1);
                            assertThat(row.o2Litres()).isCloseTo(o2Of(later), within(1e-6));
                        });
        assertThat(consumption(crew.adminEmail(), crew.teamId(), day(6), day(10)).rows()).isEmpty();
    }

    @Test
    void aPeriodThatEndsBeforeItStartsIsABadRequest() {
        final var crew = crew();
        final var detail =
                badRequestDetail(
                        send(
                                HttpMethod.GET,
                                reportsUri(crew.teamId())
                                        + "/consumption?from="
                                        + day(5).toEpochMilli()
                                        + "&to="
                                        + day(2).toEpochMilli(),
                                crew.adminEmail(),
                                null));
        assertThat(detail).isEqualTo("'from' must be before 'to'.");
    }

    @Test
    void lossesListOnlyClosedBanksWithTheReportOfTheirDetail() {
        final var crew = crew();
        fillFor(crew, crew.b().memberId(), 80);
        assertThat(losses(crew.adminEmail(), crew.teamId()).vessels()).isEmpty();

        close(crew);

        final var detail = getVessel(crew.adminEmail(), crew.teamId(), crew.bank().vesselId());
        assertThat(losses(crew.adminEmail(), crew.teamId()).vessels())
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.vessel().vesselId()).isEqualTo(crew.bank().vesselId());
                            assertThat(entry.report()).isEqualTo(detail.lossReport());
                        });
    }

    @Test
    void policyComparisonMatchesTheBalancesForATrueUpTeamAndSumsToZeroOnceClosed() {
        final var crew = crew();
        fillFor(crew, crew.b().memberId(), 80);
        fillFor(crew, crew.c().memberId(), 40);
        recordPayment(
                crew.adminEmail(),
                crew.teamId(),
                paymentBody(crew.b().memberId(), crew.a(), "50.00"));

        final var balances = balancesOf(crew.adminEmail(), crew.teamId());
        final var open = comparison(crew.adminEmail(), crew.teamId());
        assertThat(open.members()).hasSize(3);
        for (final var row : open.members()) {
            // every bank already is TRUE_UP, so that column is the real balance
            assertThat(row.trueUp())
                    .isEqualTo(balanceOf(balances, row.member().memberId()).balance());
            assertThat(row.delta()).isEqualByComparingTo(row.pooled().subtract(row.trueUp()));
        }

        close(crew);

        final var closed = comparison(crew.adminEmail(), crew.teamId());
        final var tolerance = new BigDecimal("0.03"); // one rounding cent per member
        assertThat(
                        closed.members().stream()
                                .map(PolicyComparison.Row::trueUp)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .abs())
                .isLessThanOrEqualTo(tolerance);
        assertThat(
                        closed.members().stream()
                                .map(PolicyComparison.Row::pooled)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .abs())
                .isLessThanOrEqualTo(tolerance);
    }

    @Test
    void everyReportIsNotFoundForNonMembers() {
        final var crew = crew();
        final var outsider = uniqueEmail("outsider");
        for (final var report : new String[] {"/consumption", "/losses", "/policy-comparison"}) {
            send(HttpMethod.GET, reportsUri(crew.teamId()) + report, outsider, null)
                    .expectStatus()
                    .isNotFound();
        }
    }

    // --- helpers ------------------------------------------------------------------------

    private static String reportsUri(final long teamId) {
        return "/v1/teams/" + teamId + "/reports";
    }

    private static double o2Of(final Fill fill) {
        return fill.stages().stream().mapToDouble(StageResult::addedO2Litres).sum();
    }

    private ConsumptionReport consumption(
            final String email,
            final long teamId,
            final @Nullable Instant from,
            final @Nullable Instant to) {
        final var period =
                from == null || to == null
                        ? ""
                        : "?from=" + from.toEpochMilli() + "&to=" + to.toEpochMilli();
        return Objects.requireNonNull(
                send(HttpMethod.GET, reportsUri(teamId) + "/consumption" + period, email, null)
                        .expectStatus()
                        .isOk()
                        .expectBody(ConsumptionReport.class)
                        .returnResult()
                        .getResponseBody());
    }

    private LossesReport losses(final String email, final long teamId) {
        return Objects.requireNonNull(
                send(HttpMethod.GET, reportsUri(teamId) + "/losses", email, null)
                        .expectStatus()
                        .isOk()
                        .expectBody(LossesReport.class)
                        .returnResult()
                        .getResponseBody());
    }

    private PolicyComparison comparison(final String email, final long teamId) {
        return Objects.requireNonNull(
                send(HttpMethod.GET, reportsUri(teamId) + "/policy-comparison", email, null)
                        .expectStatus()
                        .isOk()
                        .expectBody(PolicyComparison.class)
                        .returnResult()
                        .getResponseBody());
    }
}
