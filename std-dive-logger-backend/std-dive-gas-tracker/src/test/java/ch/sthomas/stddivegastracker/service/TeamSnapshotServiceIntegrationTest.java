package ch.sthomas.stddivegastracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToMember;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToVessel;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Draw;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.LossShare;
import ch.sthomas.stddivegastracker.calc.gas.GasContent;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.calc.gas.VirialFit;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.team.Team;
import ch.sthomas.stddivegastracker.model.team.TeamMembership;
import ch.sthomas.stddivegastracker.support.AbstractVesselIntegrationTest;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** DOMAIN §6, §7 end to end: stored facts -> replay + engine, incl. a house mix (§7.4, §11.6). */
final class TeamSnapshotServiceIntegrationTest extends AbstractVesselIntegrationTest {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final GasMix EAN32 = new GasMix(0.32, 0);

    @Autowired TeamSnapshotService snapshots;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void aHouseMixCarriesTheUpstreamCostAndStaysAnEstimateWhileItsSourceIsOpenTrueUp() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var o2 = createO2Bank(admin, team.teamId(), team.memberId(), "TRUE_UP");
        final var nitroxBody = bank("EAN32", EAN32);
        nitroxBody.put("waterVolumeLitres", 100);
        nitroxBody.put("lossPolicy", "POOLED");
        final var nitrox = createVessel(admin, team.teamId(), nitroxBody);
        // 2000 L O2 + 4000 L air into the nitrox bank, then a diver draws 1000 L of it
        final var blend = newFill(team.teamId(), team.memberId(), T0.plus(Duration.ofDays(1)));
        blend.setVesselTarget(nitrox.vesselId());
        saveFill(blend, drawStage(0, o2.vesselId(), GasMix.OXYGEN, 2000, 0), airStage(1, 4000));
        cylinderDraw(
                team.teamId(),
                team.memberId(),
                nitrox.vesselId(),
                EAN32,
                T0.plus(Duration.ofDays(2)),
                1000,
                0,
                false);
        // voided: counts nowhere
        cylinderDraw(
                team.teamId(),
                team.memberId(),
                o2.vesselId(),
                GasMix.OXYGEN,
                T0.plus(Duration.ofDays(2)),
                300,
                0,
                true);

        final var state = snapshots.snapshot(membership(team));

        // what the engine is fed: two draws, the transfer into a vessel and the diver's charge
        assertThat(state.input().draws())
                .extracting(Draw::sourceVesselId, Draw::litres, Draw::beneficiary)
                .containsExactlyInAnyOrder(
                        tuple(o2.vesselId(), 2000.0, new ToVessel(nitrox.vesselId())),
                        tuple(nitrox.vesselId(), 1000.0, new ToMember(team.memberId())));
        final var nitroxOpenLitres = GasContent.contentLitres(100, 0, EAN32, VirialFit.INSTANCE);
        assertThat(state.inventoryOf(nitrox.vesselId()).inflowLitres())
                .isEqualTo(nitroxOpenLitres + 6000);
        assertThat(state.inventoryOf(nitrox.vesselId()).drawnLitres()).isEqualTo(1000);
        assertThat(state.inventoryOf(o2.vesselId()).drawnLitres()).isEqualTo(2000);

        // DOMAIN §7.3: O2 is open TRUE_UP, rate = 600 / max(V_in - V_res, 2000)
        final var o2Usable =
                bd(GasContent.contentLitres(50, 200, GasMix.OXYGEN, VirialFit.INSTANCE))
                        .subtract(
                                bd(
                                        GasContent.contentLitres(
                                                50, 20, GasMix.OXYGEN, VirialFit.INSTANCE)),
                                MC);
        final var o2Rate = bd(600).divide(o2Usable.max(bd(2000)), MC);
        final var transferred = o2Rate.multiply(bd(2000), MC);
        final var o2Allocation = state.allocation().vessel(o2.vesselId());
        assertThat(o2Allocation.rate()).isEqualByComparingTo(o2Rate);
        assertThat(o2Allocation.provisional()).isTrue();
        // §7.2: the nitrox bank's cost is the O2 it received, and its price is an estimate too
        final var nitroxAllocation = state.allocation().vessel(nitrox.vesselId());
        assertThat(nitroxAllocation.totalCost()).isEqualByComparingTo(transferred);
        assertThat(nitroxAllocation.provisional()).isTrue();
        assertThat(nitroxAllocation.rate())
                .isEqualByComparingTo(transferred.divide(bd(nitroxOpenLitres + 6000), MC));
        final var dive =
                state.input().draws().stream()
                        .filter(d -> d.beneficiary() instanceof ToMember)
                        .findFirst()
                        .orElseThrow();
        assertThat(state.allocation().drawCharge(dive.drawId()).charge())
                .isEqualByComparingTo(nitroxAllocation.rate().multiply(bd(1000), MC));

        final var shown = getVessel(admin, team.teamId(), nitrox.vesselId());
        assertThat(shown.totalCost().toPlainString()).isEqualTo(money(transferred));
        assertThat(shown.rateIsEstimate()).isTrue();
        assertThat(shown.rate().toPlainString())
                .isEqualTo(
                        transferred
                                .divide(bd(nitroxOpenLitres + 6000), MC)
                                .setScale(4, RoundingMode.HALF_EVEN)
                                .toPlainString());

        // closing the O2 bank fixes its price: 600.00 / 2000 L = 0.30 per litre, 600.00 into the
        // mix
        send(
                        HttpMethod.POST,
                        vesselsUri(team.teamId()) + "/" + o2.vesselId() + "/close",
                        admin,
                        Map.of("closingPressureBar", 25))
                .expectStatus()
                .isOk();
        final var settled = getVessel(admin, team.teamId(), nitrox.vesselId());
        assertThat(settled.totalCost().toPlainString()).isEqualTo("600.00");
        assertThat(settled.rateIsEstimate()).isFalse();
        assertThat(
                        snapshots
                                .snapshot(membership(team))
                                .allocation()
                                .vessel(nitrox.vesselId())
                                .provisional())
                .isFalse();
    }

    @Test
    void activeMembersAreBoundAndNotLeftAndTheEngineFallsBackToThemForAnEmptyParticipantSnapshot() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var active = addActiveMember(admin, team.teamId());
        final var another = addMember(admin, team.teamId(), uniqueEmail("another"), Role.MEMBER);
        final var leaver = addActiveMember(admin, team.teamId());
        send(
                        HttpMethod.POST,
                        "/v1/teams/" + team.teamId() + "/members/me/leave",
                        emailOf(leaver),
                        null)
                .expectStatus()
                .isNoContent();
        // closed in the past: nobody had joined yet, the stored snapshot is empty (DOMAIN §7.3)
        final var early = createO2Bank(admin, team.teamId(), team.memberId(), "POOLED");
        close(admin, team.teamId(), early.vesselId(), T0.plus(Duration.ofDays(3)));
        final var late = createO2Bank(admin, team.teamId(), team.memberId(), "POOLED");
        close(admin, team.teamId(), late.vesselId(), null);

        final var state = snapshots.snapshot(membership(team));

        assertThat(state.input().activeMemberIds())
                .containsExactlyInAnyOrder(team.memberId(), active.memberId(), another.memberId());
        assertThat(state.members()).containsKeys(another.memberId(), leaver.memberId());
        // fallback: ACTIVE members only (no LEFT) share the loss of the early bank
        assertThat(shareHolders(state, early.vesselId()))
                .containsExactlyInAnyOrder(team.memberId(), active.memberId(), another.memberId());
        // the real snapshot: not LEFT
        assertThat(shareHolders(state, late.vesselId()))
                .containsExactlyInAnyOrder(team.memberId(), active.memberId(), another.memberId());
    }

    @Test
    void loadingATeamRunsAFixedNumberOfQueriesWhateverItsSize() {
        final var small = teamWithBanks(1);
        final var large = teamWithBanks(6);
        final var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            snapshots.snapshot(membership(small));
            final var smallQueries = statistics.getPrepareStatementCount();
            statistics.clear();
            final var state = snapshots.snapshot(membership(large));
            final var largeQueries = statistics.getPrepareStatementCount();

            assertThat(state.facts().vessels()).hasSize(6);
            assertThat(state.input().draws()).hasSize(6);
            // vessels, cost lines, readings, participants, stages, members, users
            assertThat(smallQueries).isEqualTo(7);
            assertThat(largeQueries).isEqualTo(7);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private Team teamWithBanks(final int banks) {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        addActiveMember(admin, team.teamId());
        IntStream.range(0, banks)
                .forEach(
                        _ -> {
                            final var bank =
                                    createO2Bank(admin, team.teamId(), team.memberId(), "TRUE_UP");
                            cylinderDraw(
                                    team.teamId(),
                                    team.memberId(),
                                    bank.vesselId(),
                                    GasMix.OXYGEN,
                                    T0.plus(Duration.ofDays(1)),
                                    100,
                                    1,
                                    false);
                            send(
                                            HttpMethod.POST,
                                            vesselsUri(team.teamId())
                                                    + "/"
                                                    + bank.vesselId()
                                                    + "/readings",
                                            admin,
                                            Map.of("pressureBar", 150))
                                    .expectStatus()
                                    .isOk();
                        });
        return team;
    }

    private void close(
            final String email,
            final long teamId,
            final long vesselId,
            final @Nullable Instant closedAt) {
        final var body = new HashMap<String, Object>();
        body.put("closingPressureBar", 25);
        if (closedAt != null) {
            body.put("closedAt", closedAt.toEpochMilli());
        }
        send(HttpMethod.POST, vesselsUri(teamId) + "/" + vesselId + "/close", email, body)
                .expectStatus()
                .isOk();
    }

    private static List<Long> shareHolders(
            final TeamSnapshotService.TeamState state, final long vesselId) {
        return state.allocation().vessel(vesselId).lossShares().stream()
                .map(LossShare::memberId)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static TeamMembership membership(final Team team) {
        return new TeamMembership(team.memberId(), team.teamId(), Role.ADMIN);
    }

    private static BigDecimal bd(final double value) {
        return BigDecimal.valueOf(value);
    }

    private static String money(final BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }
}
