package ch.sthomas.stddivegastracker.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;
import ch.sthomas.stddivegastracker.calc.allocation.VesselStatus;
import ch.sthomas.stddivegastracker.calc.gas.GasClass;
import ch.sthomas.stddivegastracker.calc.gas.GasContent;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.calc.gas.VirialFit;
import ch.sthomas.stddivegastracker.model.team.MemberRef;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.vessel.VesselDetail;
import ch.sthomas.stddivegastracker.model.vessel.VesselSummary;
import ch.sthomas.stddivegastracker.support.AbstractVesselIntegrationTest;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** API.md §3: authorization, validation and the numbers (expectations from DOMAIN §4, §6, §7). */
final class VesselControllerIntegrationTest extends AbstractVesselIntegrationTest {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final double V_IN = content(200, GasMix.OXYGEN);
    private static final double V_RES = content(20, GasMix.OXYGEN);

    @Autowired JdbcTemplate jdbc;

    private record Call(HttpMethod method, String pathAfterVessels, @Nullable Object body) {}

    // --- numbers ------------------------------------------------------------------------

    @Test
    void anO2BankIsPricedFromItsCostLineAndItsRateIsAnEstimateWhileOpenTrueUp() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);

        final var bank = createO2Bank(admin, team.teamId(), team.memberId(), "TRUE_UP");

        assertThat(bank.vesselId()).isPositive();
        assertThat(bank.label()).isEqualTo("O2 bank");
        assertThat(bank.gasClass()).isEqualTo(GasClass.OXYGEN);
        assertThat(bank.mix()).isEqualTo(GasMix.OXYGEN);
        assertThat(bank.waterVolumeLitres()).isEqualTo(50);
        assertThat(bank.status()).isEqualTo(VesselStatus.IN_SERVICE);
        assertThat(bank.lossPolicy()).isEqualTo(LossPolicy.TRUE_UP);
        assertThat(bank.openedAt()).isEqualTo(T0);
        assertThat(bank.closedAt()).isNull();
        assertThat(bank.initialPressureBar()).isEqualTo(200);
        assertThat(bank.expectedResidualPressureBar()).isEqualTo(20);
        assertThat(bank.estimatedPressureBar()).isCloseTo(200, within(1e-4));
        assertThat(bank.estimatedContentLitres()).isEqualTo(V_IN);
        assertThat(bank.bookContentLitres()).isEqualTo(V_IN);
        assertThat(bank.inflowLitres()).isEqualTo(V_IN);
        assertThat(bank.usableContentLitres()).isCloseTo(V_IN - V_RES, within(1e-9));
        assertThat(bank.drawnLitres()).isZero();
        // DOMAIN §7.3: open TRUE_UP, rate = C / max(V_in - V_res, D)
        assertThat(bank.rate().toPlainString()).isEqualTo(money4(openTrueUpRate(0)));
        assertThat(bank.rateIsEstimate()).isTrue();
        assertThat(bank.totalCost().toPlainString()).isEqualTo("600.00");
        assertThat(bank.allocated().toPlainString()).isEqualTo("0.00");
        assertThat(bank.unallocated().toPlainString()).isEqualTo("600.00");
        assertThat(bank.createdBy()).isEqualTo(new MemberRef(team.memberId(), nameOf(admin)));
        assertThat(bank.readings()).isEmpty();
        assertThat(bank.lossReport()).isNull();
        assertThat(bank.costLines()).hasSize(1);
        final var line = bank.costLines().getFirst();
        assertThat(line.costLineId()).isPositive();
        assertThat(line.payerMemberId()).isEqualTo(team.memberId());
        assertThat(line.payer()).isEqualTo(new MemberRef(team.memberId(), nameOf(admin)));
        assertThat(line.amount().toPlainString()).isEqualTo("600.00");
        assertThat(line.paidAt()).isAfter(T0);
        assertThat(getVessel(admin, team.teamId(), bank.vesselId())).isEqualTo(bank);
    }

    @Test
    void drawsOfActiveFillsMoveContentRateAndAllocationButVoidedFillsAreIgnored() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var bank = createO2Bank(admin, team.teamId(), team.memberId(), "TRUE_UP");
        cylinderDraw(
                team.teamId(),
                team.memberId(),
                bank.vesselId(),
                GasMix.OXYGEN,
                T0.plus(Duration.ofDays(1)),
                1000,
                10,
                false);
        cylinderDraw(
                team.teamId(),
                team.memberId(),
                bank.vesselId(),
                GasMix.OXYGEN,
                T0.plus(Duration.ofDays(1)),
                500,
                5,
                true);

        final var seen = getVessel(admin, team.teamId(), bank.vesselId());

        assertThat(seen.drawnLitres()).isEqualTo(1010);
        assertThat(seen.inflowLitres()).isEqualTo(V_IN);
        assertThat(seen.bookContentLitres()).isCloseTo(V_IN - 1010, within(1e-9));
        assertThat(seen.estimatedContentLitres()).isCloseTo(V_IN - 1010, within(1e-9));
        assertThat(seen.usableContentLitres()).isCloseTo(V_IN - 1010 - V_RES, within(1e-9));
        assertThat(seen.estimatedPressureBar())
                .isCloseTo(
                        GasContent.pressureFor(50, V_IN - 1010, GasMix.OXYGEN, VirialFit.INSTANCE),
                        within(1e-9));
        // 1010 L drawn at the open TRUE_UP rate; the rest of the 600.00 is inventory value
        assertThat(seen.rate().toPlainString()).isEqualTo(money4(openTrueUpRate(1010)));
        final var charges = openTrueUpRate(1010).multiply(bd(1010), MC);
        assertThat(seen.totalCost().toPlainString()).isEqualTo("600.00");
        assertThat(seen.allocated().toPlainString()).isEqualTo(money(charges));
        assertThat(seen.unallocated().toPlainString())
                .isEqualTo(money(bd(600).subtract(charges, MC)));
        assertThat(seen.rateIsEstimate()).isTrue();
    }

    @Test
    void aReadingShowsBookPressureAndDiscrepancyAndTheLastReadingAnchorsTheEstimate() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addActiveMember(admin, team.teamId());
        final var bank = createO2Bank(admin, team.teamId(), team.memberId(), "TRUE_UP");
        cylinderDraw(
                team.teamId(),
                team.memberId(),
                bank.vesselId(),
                GasMix.OXYGEN,
                T0.plus(Duration.ofDays(1)),
                1000,
                10,
                false);

        // posted newest first: the list comes back by measuredAt
        reading(emailOf(diver), team.teamId(), bank.vesselId(), 150, T0.plus(Duration.ofDays(2)));
        final var seen =
                reading(admin, team.teamId(), bank.vesselId(), 190, T0.plus(Duration.ofHours(12)));

        assertThat(seen.readings()).hasSize(2);
        final var early = seen.readings().getFirst();
        final var late = seen.readings().getLast();
        assertThat(early.pressureBar()).isEqualTo(190);
        assertThat(early.measuredAt()).isEqualTo(T0.plus(Duration.ofHours(12)));
        assertThat(early.createdBy().memberId()).isEqualTo(team.memberId());
        // before any draw the book still holds V_in
        assertThat(early.bookPressureBar()).isCloseTo(200, within(1e-4));
        assertThat(early.discrepancyLitres())
                .isCloseTo(V_IN - content(190, GasMix.OXYGEN), within(1e-9));
        assertThat(late.pressureBar()).isEqualTo(150);
        assertThat(late.createdBy().memberId()).isEqualTo(diver.memberId());
        assertThat(late.bookPressureBar())
                .isCloseTo(
                        GasContent.pressureFor(50, V_IN - 1010, GasMix.OXYGEN, VirialFit.INSTANCE),
                        within(1e-9));
        // discrepancy = book - measured (DOMAIN §6)
        assertThat(late.discrepancyLitres())
                .isCloseTo(V_IN - 1010 - content(150, GasMix.OXYGEN), within(1e-9));
        // the estimate is the last reading's measured content (+ nothing after it)
        assertThat(seen.estimatedPressureBar()).isCloseTo(150, within(1e-4));
        assertThat(seen.estimatedContentLitres())
                .isCloseTo(content(150, GasMix.OXYGEN), within(1e-9));
        assertThat(seen.bookContentLitres()).isCloseTo(V_IN - 1010, within(1e-9));
    }

    @Test
    void closingTrueUpWithDrawsFixesTheRateAndReportsTheLoss() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var bank = createO2Bank(admin, team.teamId(), team.memberId(), "TRUE_UP");
        cylinderDraw(
                team.teamId(),
                team.memberId(),
                bank.vesselId(),
                GasMix.OXYGEN,
                T0.plus(Duration.ofDays(1)),
                1000,
                10,
                false);

        final var closed = close(admin, team.teamId(), bank.vesselId(), 25, null);

        assertThat(closed.status()).isEqualTo(VesselStatus.CLOSED);
        assertThat(closed.closedAt()).isNotNull();
        assertThat(closed.closingPressureBar()).isEqualTo(25.0);
        // closed with D > 0: rate = C / D, final, Σ charges = C
        assertThat(closed.rate().toPlainString()).isEqualTo(money4(bd(600).divide(bd(1010), MC)));
        assertThat(closed.rateIsEstimate()).isFalse();
        assertThat(closed.allocated().toPlainString()).isEqualTo("600.00");
        assertThat(closed.unallocated().toPlainString()).isEqualTo("0.00");
        final var report = Objects.requireNonNull(closed.lossReport());
        final var residual = content(25, GasMix.OXYGEN);
        assertThat(report.inflowLitres()).isEqualTo(V_IN);
        assertThat(report.drawnLitres()).isEqualTo(1010);
        assertThat(report.lineLossLitres()).isEqualTo(10);
        assertThat(report.residualLitres()).isCloseTo(residual, within(1e-9));
        assertThat(report.unaccountedLitres()).isCloseTo(V_IN - 1010 - residual, within(1e-9));
        assertThat(report.lossFraction()).isCloseTo((V_IN - 1010) / V_IN, within(1e-12));
        assertThat(report.lossAmount()).isNull();
        assertThat(report.writeOffAmount()).isNull();
    }

    @Test
    void closingAPooledBankShowsTheLossAmountAndKeepsTheNominalRate() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var bank = createO2Bank(admin, team.teamId(), team.memberId(), "POOLED");
        cylinderDraw(
                team.teamId(),
                team.memberId(),
                bank.vesselId(),
                GasMix.OXYGEN,
                T0.plus(Duration.ofDays(1)),
                1000,
                10,
                false);
        final var nominal = bd(600).divide(bd(V_IN), MC);

        assertThat(getVessel(admin, team.teamId(), bank.vesselId()).rate().toPlainString())
                .isEqualTo(money4(nominal));
        // POOLED is final from the start: not an estimate although the bank is open
        assertThat(getVessel(admin, team.teamId(), bank.vesselId()).rateIsEstimate()).isFalse();

        final var closed = close(admin, team.teamId(), bank.vesselId(), 25, null);

        assertThat(closed.rate().toPlainString()).isEqualTo(money4(nominal));
        assertThat(closed.rateIsEstimate()).isFalse();
        final var report = Objects.requireNonNull(closed.lossReport());
        // DOMAIN §7.3: loss = C - rate * D, shared by the closing participants
        assertThat(Objects.requireNonNull(report.lossAmount()).toPlainString())
                .isEqualTo(money(bd(600).subtract(nominal.multiply(bd(1010), MC), MC)));
        assertThat(report.writeOffAmount()).isNull();
        assertThat(closed.allocated().toPlainString()).isEqualTo("600.00");
        assertThat(closed.unallocated().toPlainString()).isEqualTo("0.00");
    }

    @Test
    void closingATrueUpBankWithoutDrawsWritesOffItsCostAndShowsRateZero() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var bank = createO2Bank(admin, team.teamId(), team.memberId(), "TRUE_UP");

        final var closed = close(admin, team.teamId(), bank.vesselId(), 190, null);

        assertThat(closed.rate().toPlainString()).isEqualTo("0.0000");
        assertThat(closed.rateIsEstimate()).isFalse();
        final var report = Objects.requireNonNull(closed.lossReport());
        assertThat(Objects.requireNonNull(report.writeOffAmount()).toPlainString())
                .isEqualTo("600.00");
        assertThat(report.lossAmount()).isNull();
        assertThat(report.drawnLitres()).isZero();
        assertThat(closed.allocated().toPlainString()).isEqualTo("600.00");
        assertThat(closed.unallocated().toPlainString()).isEqualTo("0.00");
    }

    @Test
    void aCostLineAddedAfterClosingRepricesTheBank() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var bank = createO2Bank(admin, team.teamId(), team.memberId(), "TRUE_UP");
        cylinderDraw(
                team.teamId(),
                team.memberId(),
                bank.vesselId(),
                GasMix.OXYGEN,
                T0.plus(Duration.ofDays(1)),
                1000,
                10,
                false);
        close(admin, team.teamId(), bank.vesselId(), 25, null);

        final var repriced =
                addCostLine(admin, team.teamId(), bank.vesselId(), team.memberId(), "101.00");

        assertThat(repriced.totalCost().toPlainString()).isEqualTo("701.00");
        assertThat(repriced.rate().toPlainString()).isEqualTo(money4(bd(701).divide(bd(1010), MC)));
        assertThat(repriced.unallocated().toPlainString()).isEqualTo("0.00");
    }

    @Test
    void closingSnapshotsTheMembersWhoAreNotLeftAndHadJoined() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var active = addActiveMember(admin, team.teamId());
        final var another = addMember(admin, team.teamId(), uniqueEmail("another"), Role.MEMBER);
        final var later = addMember(admin, team.teamId(), uniqueEmail("later"), Role.MEMBER);
        jdbc.update(
                "UPDATE t_team_member SET joined_at = now() + interval '1 day' WHERE pk_team_member_id = ?",
                later.memberId());
        final var leaver = addActiveMember(admin, team.teamId());
        send(
                        HttpMethod.POST,
                        "/v1/teams/" + team.teamId() + "/members/me/leave",
                        emailOf(leaver),
                        null)
                .expectStatus()
                .isNoContent();
        final var bank = createO2Bank(admin, team.teamId(), team.memberId(), "POOLED");

        close(admin, team.teamId(), bank.vesselId(), 25, null);

        // not LEFT, not someone who joins after the closing time
        assertThat(participants(bank.vesselId()))
                .containsExactlyInAnyOrder(team.memberId(), active.memberId(), another.memberId());
    }

    @Test
    void reopeningClearsTheClosingDataAndTheParticipants() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var bank = createO2Bank(admin, team.teamId(), team.memberId(), "TRUE_UP");
        close(admin, team.teamId(), bank.vesselId(), 25, null);
        assertThat(participants(bank.vesselId())).containsExactly(team.memberId());

        final var reopened =
                detailOf(
                        vesselRequest(
                                HttpMethod.POST,
                                admin,
                                team.teamId(),
                                "/" + bank.vesselId() + "/reopen",
                                null));

        assertThat(reopened.status()).isEqualTo(VesselStatus.IN_SERVICE);
        assertThat(reopened.closedAt()).isNull();
        assertThat(reopened.closingPressureBar()).isNull();
        assertThat(reopened.lossReport()).isNull();
        assertThat(reopened.rateIsEstimate()).isTrue();
        assertThat(reopened.rate().toPlainString()).isEqualTo(money4(openTrueUpRate(0)));
        assertThat(participants(bank.vesselId())).isEmpty();
    }

    // --- create, get, list --------------------------------------------------------------

    @Test
    void aMinimalBankTakesItsDefaultsFromTheTeamAndTheCaller() {
        final var admin = uniqueEmail("admin");
        final var team =
                createTeam(
                        admin,
                        Map.of(
                                "name", "Defaults",
                                "defaultLossPolicy", "POOLED",
                                "defaultResidualPressureBar", 30));
        final var diver = addActiveMember(admin, team.teamId());
        final var before = Instant.now();

        final var air = createVessel(emailOf(diver), team.teamId(), bank("  Air  ", GasMix.AIR));

        assertThat(air.label()).isEqualTo("Air");
        assertThat(air.gasClass()).isEqualTo(GasClass.AIR);
        assertThat(air.initialPressureBar()).isZero();
        assertThat(air.expectedResidualPressureBar()).isEqualTo(30);
        assertThat(air.lossPolicy()).isEqualTo(LossPolicy.POOLED);
        assertThat(air.openedAt()).isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));
        assertThat(air.createdBy().memberId()).isEqualTo(diver.memberId());
        assertThat(air.costLines()).isEmpty();
        assertThat(air.notes()).isNull();
        // an empty bank at 0 bar still holds its water volume of gas (DOMAIN §4.1)
        assertThat(air.estimatedContentLitres()).isCloseTo(content(0, GasMix.AIR), within(1e-9));
        assertThat(air.totalCost().toPlainString()).isEqualTo("0.00");
        assertThat(air.rate().toPlainString()).isEqualTo("0.0000");
    }

    @Test
    void theJsonCarriesTheApiFieldsAndOmitsWhatIsAbsent() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var air = createVessel(admin, team.teamId(), bank("Air", GasMix.AIR));

        final var json =
                Objects.requireNonNull(
                        vesselRequest(
                                        HttpMethod.GET,
                                        admin,
                                        team.teamId(),
                                        "/" + air.vesselId(),
                                        null)
                                .expectStatus()
                                .isOk()
                                .expectBody(String.class)
                                .returnResult()
                                .getResponseBody());

        assertThat(json)
                .contains(
                        "\"vesselId\":" + air.vesselId(),
                        "\"gasClass\":\"AIR\"",
                        "\"mix\":{\"o2\":0.209,\"he\":0.0}",
                        "\"status\":\"IN_SERVICE\"",
                        "\"rate\":\"0.0000\"",
                        "\"rateIsEstimate\":true",
                        "\"totalCost\":\"0.00\"",
                        "\"allocated\":\"0.00\"",
                        "\"unallocated\":\"0.00\"",
                        "\"costLines\":[]",
                        "\"readings\":[]")
                .doesNotContain("closedAt", "closingPressureBar", "lossReport", "notes");
    }

    @Test
    void theListFiltersByStatusAndOrdersByLabelAndClosingTime() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var beta = createVessel(admin, team.teamId(), bank("beta", GasMix.AIR));
        final var alpha = createVessel(admin, team.teamId(), bank("Alpha", GasMix.AIR));
        // opened at T0, so they can be closed on days 1 and 2
        final var oldBank = bank("old", GasMix.AIR);
        oldBank.put("openedAt", T0.toEpochMilli());
        final var newBank = bank("new", GasMix.AIR);
        newBank.put("openedAt", T0.toEpochMilli());
        final var firstClosed = createVessel(admin, team.teamId(), oldBank);
        final var lastClosed = createVessel(admin, team.teamId(), newBank);
        close(admin, team.teamId(), firstClosed.vesselId(), 0, T0.plus(Duration.ofDays(1)));
        close(admin, team.teamId(), lastClosed.vesselId(), 0, T0.plus(Duration.ofDays(2)));

        assertThat(ids(list(admin, team.teamId(), "")))
                .containsExactly(alpha.vesselId(), beta.vesselId());
        assertThat(ids(list(admin, team.teamId(), "?status=IN_SERVICE")))
                .containsExactly(alpha.vesselId(), beta.vesselId());
        assertThat(ids(list(admin, team.teamId(), "?status=CLOSED")))
                .containsExactly(lastClosed.vesselId(), firstClosed.vesselId());
        assertThat(ids(list(admin, team.teamId(), "?status=ALL")))
                .containsExactly(
                        alpha.vesselId(),
                        beta.vesselId(),
                        lastClosed.vesselId(),
                        firstClosed.vesselId());
        final var summary = list(admin, team.teamId(), "?status=CLOSED").getFirst();
        assertThat(summary.status()).isEqualTo(VesselStatus.CLOSED);
        assertThat(summary.closedAt()).isEqualTo(T0.plus(Duration.ofDays(2)));
        assertThat(summary.rate().toPlainString()).isEqualTo("0.0000");
        vesselRequest(HttpMethod.GET, admin, team.teamId(), "?status=BOGUS", null)
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void aVesselOfAnotherTeamIsNotFoundThroughThisTeamsPath() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var otherAdmin = uniqueEmail("other");
        final var otherTeam = createTeam(otherAdmin);
        final var foreign =
                createVessel(otherAdmin, otherTeam.teamId(), bank("Foreign", GasMix.AIR));
        final var path = "/" + foreign.vesselId();

        assertThat(list(admin, team.teamId(), "?status=ALL")).isEmpty();
        for (final var call :
                List.of(
                        new Call(HttpMethod.GET, path, null),
                        new Call(HttpMethod.PUT, path, Map.of("label", "Mine now")),
                        new Call(HttpMethod.POST, path + "/close", Map.of("closingPressureBar", 5)),
                        new Call(HttpMethod.POST, path + "/reopen", null),
                        new Call(HttpMethod.POST, path + "/readings", Map.of("pressureBar", 5)),
                        new Call(
                                HttpMethod.POST,
                                path + "/cost-lines",
                                Map.of("payerMemberId", team.memberId(), "amount", "5.00")),
                        new Call(HttpMethod.DELETE, path + "/cost-lines/1", null))) {
            vesselRequest(call.method(), admin, team.teamId(), call.pathAfterVessels(), call.body())
                    .expectStatus()
                    .isNotFound();
        }
        assertThat(getVessel(otherAdmin, otherTeam.teamId(), foreign.vesselId()).label())
                .isEqualTo("Foreign");
    }

    // --- authorization ------------------------------------------------------------------

    @Test
    void everyEndpointIsNotFoundForNonMembersLeftMembersAndUnknownTeams() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var leaver = addActiveMember(admin, team.teamId());
        send(
                        HttpMethod.POST,
                        "/v1/teams/" + team.teamId() + "/members/me/leave",
                        emailOf(leaver),
                        null)
                .expectStatus()
                .isNoContent();
        final var bank = createO2Bank(admin, team.teamId(), team.memberId(), "TRUE_UP");
        final var costLineId = bank.costLines().getFirst().costLineId();
        final var vessel = "/" + bank.vesselId();
        final var calls =
                List.of(
                        new Call(HttpMethod.GET, "", null),
                        new Call(HttpMethod.POST, "", bank("Mine", GasMix.AIR)),
                        new Call(HttpMethod.GET, vessel, null),
                        new Call(HttpMethod.PUT, vessel, Map.of("label", "x")),
                        new Call(
                                HttpMethod.POST,
                                vessel + "/close",
                                Map.of("closingPressureBar", 5)),
                        new Call(HttpMethod.POST, vessel + "/reopen", null),
                        new Call(
                                HttpMethod.POST,
                                vessel + "/cost-lines",
                                Map.of("payerMemberId", team.memberId(), "amount", "5.00")),
                        new Call(HttpMethod.DELETE, vessel + "/cost-lines/" + costLineId, null),
                        new Call(HttpMethod.POST, vessel + "/readings", Map.of("pressureBar", 5)));

        for (final var caller : List.of(uniqueEmail("stranger"), emailOf(leaver))) {
            for (final var call : calls) {
                vesselRequest(
                                call.method(),
                                caller,
                                team.teamId(),
                                call.pathAfterVessels(),
                                call.body())
                        .expectStatus()
                        .isNotFound();
            }
        }
        for (final var call : calls) {
            send(
                            call.method(),
                            "/v1/teams/999999999/vessels" + call.pathAfterVessels(),
                            admin,
                            call.body())
                    .expectStatus()
                    .isNotFound();
        }
        assertThat(getVessel(admin, team.teamId(), bank.vesselId()).status())
                .isEqualTo(VesselStatus.IN_SERVICE);
    }

    @Test
    void adminOnlyActionsAreForbiddenForPlainMembers() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addActiveMember(admin, team.teamId());
        final var bank = createO2Bank(admin, team.teamId(), team.memberId(), "TRUE_UP");
        final var vessel = "/" + bank.vesselId();
        final var costLineId = bank.costLines().getFirst().costLineId();

        for (final var call :
                List.of(
                        new Call(HttpMethod.POST, vessel + "/reopen", null),
                        new Call(HttpMethod.DELETE, vessel + "/cost-lines/" + costLineId, null),
                        new Call(
                                HttpMethod.POST,
                                vessel + "/cost-lines",
                                Map.of("payerMemberId", team.memberId(), "amount", "5.00")),
                        new Call(
                                HttpMethod.PUT, vessel, Map.of("mix", Map.of("o2", 0.32, "he", 0))),
                        new Call(HttpMethod.PUT, vessel, Map.of("lossPolicy", "POOLED")),
                        // not the creator, not an admin
                        new Call(HttpMethod.PUT, vessel, Map.of("label", "Mine")),
                        new Call(
                                HttpMethod.POST,
                                vessel + "/close",
                                Map.of("closingPressureBar", 5)))) {
            vesselRequest(
                            call.method(),
                            emailOf(diver),
                            team.teamId(),
                            call.pathAfterVessels(),
                            call.body())
                    .expectStatus()
                    .isForbidden();
        }
        // a member creating a vessel cannot book the first cost line to someone else either
        final var body = bank("Mine", GasMix.AIR);
        body.put(
                "initialCostLines",
                List.of(Map.of("payerMemberId", team.memberId(), "amount", "5.00")));
        vesselRequest(HttpMethod.POST, emailOf(diver), team.teamId(), "", body)
                .expectStatus()
                .isForbidden();
        assertThat(list(admin, team.teamId(), "")).hasSize(1);
        assertThat(getVessel(admin, team.teamId(), bank.vesselId()))
                .extracting(VesselDetail::label, VesselDetail::lossPolicy, VesselDetail::status)
                .containsExactly("O2 bank", LossPolicy.TRUE_UP, VesselStatus.IN_SERVICE);
        assertThat(getVessel(admin, team.teamId(), bank.vesselId()).costLines()).hasSize(1);
    }

    @Test
    void aBankCanBePaidBySeveralPeopleWithDifferentOrTheSameAmounts() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var other = addActiveMember(admin, team.teamId());
        final var third = addActiveMember(admin, team.teamId());
        final var body = bank("Shared", GasMix.OXYGEN);
        body.put(
                "initialCostLines",
                List.of(
                        Map.of("payerMemberId", team.memberId(), "amount", "100.00"),
                        Map.of("payerMemberId", other.memberId(), "amount", "50.00"),
                        Map.of("payerMemberId", third.memberId(), "amount", "50.00")));

        final var created = createVessel(admin, team.teamId(), body);

        final var detail = getVessel(admin, team.teamId(), created.vesselId());
        assertThat(detail.totalCost()).isEqualByComparingTo("200.00");
        assertThat(detail.costLines())
                .extracting(line -> line.payer().memberId(), line -> line.amount().toPlainString())
                .containsExactlyInAnyOrder(
                        tuple(team.memberId(), "100.00"),
                        tuple(other.memberId(), "50.00"),
                        tuple(third.memberId(), "50.00"));
    }

    @Test
    void theCreatorMayCloseAndEditTheirBankButNotMixOrPolicyAndAnAdminMayCloseAnyBank() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var creator = addActiveMember(admin, team.teamId());
        final var bystander = addActiveMember(admin, team.teamId());
        final var mine = createVessel(emailOf(creator), team.teamId(), bank("Mine", GasMix.AIR));
        final var other =
                createVessel(emailOf(bystander), team.teamId(), bank("Other", GasMix.AIR));
        final var path = "/" + mine.vesselId();

        final var edited =
                detailOf(
                        vesselRequest(
                                HttpMethod.PUT,
                                emailOf(creator),
                                team.teamId(),
                                path,
                                Map.of(
                                        "label",
                                        " Renamed ",
                                        "notes",
                                        "left tank",
                                        "expectedResidualPressureBar",
                                        15)));
        assertThat(edited.label()).isEqualTo("Renamed");
        assertThat(edited.notes()).isEqualTo("left tank");
        assertThat(edited.expectedResidualPressureBar()).isEqualTo(15);
        vesselRequest(
                        HttpMethod.PUT,
                        emailOf(creator),
                        team.teamId(),
                        path,
                        Map.of("lossPolicy", "POOLED"))
                .expectStatus()
                .isForbidden();
        vesselRequest(
                        HttpMethod.POST,
                        emailOf(bystander),
                        team.teamId(),
                        path + "/close",
                        Map.of("closingPressureBar", 3))
                .expectStatus()
                .isForbidden();

        assertThat(close(emailOf(creator), team.teamId(), mine.vesselId(), 3, null).status())
                .isEqualTo(VesselStatus.CLOSED);
        assertThat(close(admin, team.teamId(), other.vesselId(), 3, null).status())
                .isEqualTo(VesselStatus.CLOSED);
    }

    @Test
    void anAdminChangesMixAndPolicyButNeverThePolicyOfAClosedBank() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var bank = createVessel(admin, team.teamId(), bank("EAN", GasMix.AIR));
        final var path = "/" + bank.vesselId();

        final var changed =
                detailOf(
                        vesselRequest(
                                HttpMethod.PUT,
                                admin,
                                team.teamId(),
                                path,
                                Map.of(
                                        "mix",
                                        Map.of("o2", 0.32, "he", 0),
                                        "lossPolicy",
                                        "POOLED")));
        assertThat(changed.mix()).isEqualTo(new GasMix(0.32, 0));
        assertThat(changed.gasClass()).isEqualTo(GasClass.NITROX);
        assertThat(changed.lossPolicy()).isEqualTo(LossPolicy.POOLED);

        close(admin, team.teamId(), bank.vesselId(), 0, null);
        vesselRequest(HttpMethod.PUT, admin, team.teamId(), path, Map.of("lossPolicy", "TRUE_UP"))
                .expectStatus()
                .isEqualTo(409);
        // the rest stays editable, an empty note clears the note
        vesselRequest(HttpMethod.PUT, admin, team.teamId(), path, Map.of("notes", "after closing"))
                .expectStatus()
                .isOk();
        final var cleared =
                detailOf(
                        vesselRequest(
                                HttpMethod.PUT, admin, team.teamId(), path, Map.of("notes", "  ")));
        assertThat(cleared.notes()).isNull();
        assertThat(cleared.lossPolicy()).isEqualTo(LossPolicy.POOLED);
    }

    // --- state conflicts ----------------------------------------------------------------

    @Test
    void closingTwiceAndReopeningAnOpenBankAreConflicts() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var bank = createVessel(admin, team.teamId(), bank("Bank", GasMix.AIR));
        final var path = "/" + bank.vesselId();

        vesselRequest(HttpMethod.POST, admin, team.teamId(), path + "/reopen", null)
                .expectStatus()
                .isEqualTo(409);
        close(admin, team.teamId(), bank.vesselId(), 10, null);
        vesselRequest(
                        HttpMethod.POST,
                        admin,
                        team.teamId(),
                        path + "/close",
                        Map.of("closingPressureBar", 5))
                .expectStatus()
                .isEqualTo(409);
        assertThat(getVessel(admin, team.teamId(), bank.vesselId()).closingPressureBar())
                .isEqualTo(10.0);
    }

    // --- cost lines ---------------------------------------------------------------------

    @Test
    void aMemberBooksTheirOwnCostLineButAnotherPayerNeedsAnAdminAndMustBeAMemberWhoHasNotLeft() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addActiveMember(admin, team.teamId());
        final var another = addMember(admin, team.teamId(), uniqueEmail("another"), Role.MEMBER);
        final var leaver = addActiveMember(admin, team.teamId());
        send(
                        HttpMethod.POST,
                        "/v1/teams/" + team.teamId() + "/members/me/leave",
                        emailOf(leaver),
                        null)
                .expectStatus()
                .isNoContent();
        final var otherTeam = createTeam(uniqueEmail("other"));
        final var bank = createVessel(admin, team.teamId(), bank("Bank", GasMix.AIR));
        final var path = "/" + bank.vesselId() + "/cost-lines";

        final var own =
                addCostLine(
                        emailOf(diver), team.teamId(), bank.vesselId(), diver.memberId(), "40.50");
        assertThat(own.costLines()).hasSize(1);
        assertThat(own.costLines().getFirst().payer().memberId()).isEqualTo(diver.memberId());
        assertThat(own.costLines().getFirst().note()).isNull();

        // an admin books for another member: fine
        final var forAnother =
                detailOf(
                        vesselRequest(
                                HttpMethod.POST,
                                admin,
                                team.teamId(),
                                path,
                                Map.of(
                                        "payerMemberId",
                                        another.memberId(),
                                        "amount",
                                        "9.50",
                                        "note",
                                        " rental ",
                                        "paidAt",
                                        T0.toEpochMilli())));
        assertThat(forAnother.totalCost().toPlainString()).isEqualTo("50.00");
        final var anotherLine = forAnother.costLines().getFirst();
        assertThat(anotherLine.payer())
                .isEqualTo(new MemberRef(another.memberId(), nameOf(emailOf(another))));
        assertThat(anotherLine.note()).isEqualTo("rental");
        assertThat(anotherLine.paidAt()).isEqualTo(T0);

        for (final var payerId :
                List.of(leaver.memberId(), otherTeam.memberId(), 0L, 999_999_999L)) {
            vesselRequest(
                            HttpMethod.POST,
                            admin,
                            team.teamId(),
                            path,
                            Map.of("payerMemberId", payerId, "amount", "5.00"))
                    .expectStatus()
                    .isBadRequest();
        }
        assertThat(getVessel(admin, team.teamId(), bank.vesselId()).costLines()).hasSize(2);
    }

    @Test
    void costLineAmountsMustBePositiveWithAtMostTwoDecimals() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var bank = createVessel(admin, team.teamId(), bank("Bank", GasMix.AIR));
        final var path = "/" + bank.vesselId() + "/cost-lines";

        for (final var amount :
                List.<Object>of("0", "0.00", "-5.00", "12.345", "1000000000", "abc", "")) {
            vesselRequest(
                            HttpMethod.POST,
                            admin,
                            team.teamId(),
                            path,
                            Map.of("payerMemberId", team.memberId(), "amount", amount))
                    .expectStatus()
                    .isBadRequest();
        }
        vesselRequest(
                        HttpMethod.POST,
                        admin,
                        team.teamId(),
                        path,
                        Map.of("payerMemberId", team.memberId()))
                .expectStatus()
                .isBadRequest();
        assertThat(getVessel(admin, team.teamId(), bank.vesselId()).costLines()).isEmpty();

        // a trailing zero is not a third decimal; a plain JSON number works as well
        assertThat(
                        addCostLine(
                                        admin,
                                        team.teamId(),
                                        bank.vesselId(),
                                        team.memberId(),
                                        "12.340")
                                .costLines()
                                .getFirst()
                                .amount()
                                .toPlainString())
                .isEqualTo("12.34");
        final var number =
                detailOf(
                        vesselRequest(
                                HttpMethod.POST,
                                admin,
                                team.teamId(),
                                path,
                                Map.of("payerMemberId", team.memberId(), "amount", 7)));
        assertThat(number.totalCost().toPlainString()).isEqualTo("19.34");
        assertThat(number.costLines())
                .extracting(line -> line.amount().toPlainString())
                .containsExactly("12.34", "7.00");
    }

    @Test
    void anAdminDeletesACostLineAndTheBankIsRepricedOrTheLineIsNotFound() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var bank = createO2Bank(admin, team.teamId(), team.memberId(), "TRUE_UP");
        final var extra =
                addCostLine(admin, team.teamId(), bank.vesselId(), team.memberId(), "100.00");
        assertThat(extra.totalCost().toPlainString()).isEqualTo("700.00");
        final var extraId = extra.costLines().getLast().costLineId();
        final var other = createVessel(admin, team.teamId(), bank("Other", GasMix.AIR));

        final var path = "/" + bank.vesselId() + "/cost-lines/";
        final var after =
                detailOf(
                        vesselRequest(
                                HttpMethod.DELETE, admin, team.teamId(), path + extraId, null));

        assertThat(after.totalCost().toPlainString()).isEqualTo("600.00");
        assertThat(after.costLines()).hasSize(1);
        assertThat(after.rate().toPlainString()).isEqualTo(money4(openTrueUpRate(0)));
        vesselRequest(HttpMethod.DELETE, admin, team.teamId(), path + extraId, null)
                .expectStatus()
                .isNotFound();
        // a cost line of another bank of the same team is not found through this bank
        vesselRequest(
                        HttpMethod.DELETE,
                        admin,
                        team.teamId(),
                        "/"
                                + other.vesselId()
                                + "/cost-lines/"
                                + after.costLines().getFirst().costLineId(),
                        null)
                .expectStatus()
                .isNotFound();
        assertThat(getVessel(admin, team.teamId(), bank.vesselId()).costLines()).hasSize(1);
    }

    // --- readings -----------------------------------------------------------------------

    @Test
    void readingsMustBeBetweenZeroAndFourHundredBarAndDefaultToNow() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var bank = createVessel(admin, team.teamId(), bank("Bank", GasMix.AIR));
        final var path = "/" + bank.vesselId() + "/readings";

        for (final var body :
                List.<Object>of(
                        Map.of("pressureBar", 400.5),
                        Map.of("pressureBar", -1),
                        Map.of("note", "no pressure"))) {
            vesselRequest(HttpMethod.POST, admin, team.teamId(), path, body)
                    .expectStatus()
                    .isBadRequest();
        }
        final var before = Instant.now();
        final var seen =
                detailOf(
                        vesselRequest(
                                HttpMethod.POST,
                                admin,
                                team.teamId(),
                                path,
                                Map.of("pressureBar", 400)));

        assertThat(seen.readings()).hasSize(1);
        assertThat(seen.readings().getFirst().pressureBar()).isEqualTo(400);
        assertThat(seen.readings().getFirst().measuredAt())
                .isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));
        detailOf(
                vesselRequest(
                        HttpMethod.POST, admin, team.teamId(), path, Map.of("pressureBar", 0)));
    }

    // --- validation ---------------------------------------------------------------------

    @Test
    void invalidBanksAreRejected() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var bodies = new ArrayList<Map<String, Object>>();
        bodies.add(with(bank("Bad mix", GasMix.AIR), "mix", Map.of("o2", 0.8, "he", 0.5)));
        bodies.add(with(bank("Bad o2", GasMix.AIR), "mix", Map.of("o2", 1.5, "he", 0)));
        bodies.add(with(bank("Negative he", GasMix.AIR), "mix", Map.of("o2", 0.2, "he", -0.1)));
        bodies.add(without(bank("No mix", GasMix.AIR), "mix"));
        bodies.add(with(bank("No volume", GasMix.AIR), "waterVolumeLitres", 0));
        bodies.add(without(bank("No volume", GasMix.AIR), "waterVolumeLitres"));
        bodies.add(with(bank("Huge", GasMix.AIR), "waterVolumeLitres", 5000.1));
        bodies.add(with(bank("Pressure", GasMix.AIR), "initialPressureBar", 401));
        bodies.add(with(bank("Residual", GasMix.AIR), "expectedResidualPressureBar", 400.5));
        bodies.add(with(bank("   ", GasMix.AIR), "notes", "x"));
        bodies.add(bank("x".repeat(61), GasMix.AIR));
        bodies.add(with(bank("Notes", GasMix.AIR), "notes", "n".repeat(1001)));
        bodies.add(with(bank("Policy", GasMix.AIR), "lossPolicy", "SOMETIMES"));
        bodies.add(
                with(
                        bank("Cost", GasMix.AIR),
                        "initialCostLines",
                        List.of(Map.of("payerMemberId", team.memberId(), "amount", "0"))));
        bodies.add(
                with(
                        bank("Payer", GasMix.AIR),
                        "initialCostLines",
                        List.of(Map.of("payerMemberId", 999_999_999L, "amount", "5.00"))));
        bodies.add(
                with(
                        bank("One bad payer", GasMix.AIR),
                        "initialCostLines",
                        List.of(
                                Map.of("payerMemberId", team.memberId(), "amount", "5.00"),
                                Map.of("payerMemberId", 999_999_999L, "amount", "5.00"))));
        bodies.add(
                with(
                        bank("No payer", GasMix.AIR),
                        "initialCostLines",
                        Collections.singletonList(null)));

        for (final var body : bodies) {
            vesselRequest(HttpMethod.POST, admin, team.teamId(), "", body)
                    .expectStatus()
                    .isBadRequest();
        }
        // a rejected initial cost line leaves no half-created bank behind
        assertThat(list(admin, team.teamId(), "?status=ALL")).isEmpty();
        // the limits themselves are fine
        final var limits = bank("x".repeat(60), GasMix.HELIUM);
        limits.put("waterVolumeLitres", 5000);
        limits.put("initialPressureBar", 400);
        limits.put("expectedResidualPressureBar", 400);
        assertThat(createVessel(admin, team.teamId(), limits).gasClass())
                .isEqualTo(GasClass.HELIUM);
    }

    @Test
    void invalidUpdatesAndClosingPressuresAreRejected() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var bank = createVessel(admin, team.teamId(), bank("Bank", GasMix.AIR));
        final var path = "/" + bank.vesselId();

        for (final var body :
                List.<Object>of(
                        Map.of("label", " "),
                        Map.of("expectedResidualPressureBar", 401),
                        Map.of("mix", Map.of("o2", 0.8, "he", 0.5)),
                        Map.of("lossPolicy", "NEVER"))) {
            vesselRequest(HttpMethod.PUT, admin, team.teamId(), path, body)
                    .expectStatus()
                    .isBadRequest();
        }
        for (final var body :
                List.<Object>of(
                        Map.of("closingPressureBar", 401),
                        Map.of("closingPressureBar", -1),
                        Map.of("closedAt", 1))) {
            vesselRequest(HttpMethod.POST, admin, team.teamId(), path + "/close", body)
                    .expectStatus()
                    .isBadRequest();
        }
        assertThat(getVessel(admin, team.teamId(), bank.vesselId()))
                .extracting(VesselDetail::label, VesselDetail::status)
                .containsExactly("Bank", VesselStatus.IN_SERVICE);
    }

    // --- helpers ------------------------------------------------------------------------

    private VesselDetail close(
            final String email,
            final long teamId,
            final long vesselId,
            final double closingPressureBar,
            final @Nullable Instant closedAt) {
        final var body = new HashMap<String, Object>();
        body.put("closingPressureBar", closingPressureBar);
        if (closedAt != null) {
            body.put("closedAt", closedAt.toEpochMilli());
        }
        return detailOf(
                vesselRequest(HttpMethod.POST, email, teamId, "/" + vesselId + "/close", body));
    }

    private VesselDetail reading(
            final String email,
            final long teamId,
            final long vesselId,
            final double pressureBar,
            final Instant measuredAt) {
        return detailOf(
                vesselRequest(
                        HttpMethod.POST,
                        email,
                        teamId,
                        "/" + vesselId + "/readings",
                        Map.of(
                                "pressureBar",
                                pressureBar,
                                "measuredAt",
                                measuredAt.toEpochMilli())));
    }

    private VesselDetail addCostLine(
            final String email,
            final long teamId,
            final long vesselId,
            final long payerMemberId,
            final String amount) {
        return detailOf(
                vesselRequest(
                        HttpMethod.POST,
                        email,
                        teamId,
                        "/" + vesselId + "/cost-lines",
                        Map.of("payerMemberId", payerMemberId, "amount", amount)));
    }

    private List<VesselSummary> list(final String email, final long teamId, final String query) {
        return Objects.requireNonNull(
                vesselRequest(HttpMethod.GET, email, teamId, query, null)
                        .expectStatus()
                        .isOk()
                        .expectBody(new ParameterizedTypeReference<List<VesselSummary>>() {})
                        .returnResult()
                        .getResponseBody());
    }

    private static List<Long> ids(final List<VesselSummary> summaries) {
        return summaries.stream().map(VesselSummary::vesselId).toList();
    }

    private List<Long> participants(final long vesselId) {
        return jdbc.query(
                "SELECT fk_member_id FROM t_vessel_loss_participant WHERE fk_vessel_id = ?",
                (rs, rowNum) -> rs.getLong(1),
                vesselId);
    }

    private static Map<String, Object> with(
            final Map<String, Object> body, final String key, final Object value) {
        body.put(key, value);
        return body;
    }

    private static Map<String, Object> without(final Map<String, Object> body, final String key) {
        body.remove(key);
        return body;
    }

    /** Display name of a user who never set one: the email's local part. */
    private static String nameOf(final String email) {
        return email.substring(0, email.indexOf('@'));
    }

    private static double content(final double pressureBar, final GasMix mix) {
        return GasContent.contentLitres(50, pressureBar, mix, VirialFit.INSTANCE);
    }

    private static BigDecimal bd(final double value) {
        return BigDecimal.valueOf(value);
    }

    /** DOMAIN §7.3 open TRUE_UP on the 600.00 O2 bank: unrounded C / max(V_in - V_res, D). */
    private static BigDecimal openTrueUpRate(final double drawnLitres) {
        return bd(600).divide(bd(V_IN).subtract(bd(V_RES), MC).max(bd(drawnLitres)), MC);
    }

    private static String money(final BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    private static String money4(final BigDecimal rate) {
        return rate.setScale(4, RoundingMode.HALF_EVEN).toPlainString();
    }
}
