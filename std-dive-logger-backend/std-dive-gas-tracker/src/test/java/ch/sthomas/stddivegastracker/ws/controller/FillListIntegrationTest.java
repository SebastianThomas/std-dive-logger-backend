package ch.sthomas.stddivegastracker.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ch.sthomas.stddivegastracker.model.fill.FillInput;
import ch.sthomas.stddivegastracker.model.fill.FillStatus;
import ch.sthomas.stddivegastracker.model.fill.FillTarget;
import ch.sthomas.stddivegastracker.model.fill.StageSource;
import ch.sthomas.stddivegastracker.model.team.MemberRef;
import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.model.vessel.VesselDetail;
import ch.sthomas.stddivegastracker.service.FillService;
import ch.sthomas.stddivegastracker.support.AbstractFillIntegrationTest;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** API.md §4 list: filters, order, paging, the summary fields, and its fixed number of queries. */
final class FillListIntegrationTest extends AbstractFillIntegrationTest {

    @Autowired FillService fillService;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void theListIsNewestFirstFiltersByMemberAndBankAndHidesVoidedFillsByDefault() {
        final var room = room();
        final var alice = addActiveMember(room.adminEmail(), room.teamId());
        final var bob = addActiveMember(room.adminEmail(), room.teamId());
        final var nitrox = nitroxBank(room);
        final var trimix =
                createFill(
                        emailOf(alice),
                        room.teamId(),
                        at(trimixBody(room, alice.memberId()), day(1)));
        final var oxygenOnly =
                createFill(
                        emailOf(bob),
                        room.teamId(),
                        at(
                                fillBody(
                                        cylinder(15, bob.memberId()),
                                        0,
                                        fromBank(room.o2().vesselId(), 50),
                                        fromAir(200)),
                                day(2)));
        final var transfer =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        at(
                                fillBody(
                                        bankTarget(nitrox.vesselId()),
                                        0,
                                        fromBank(room.o2().vesselId(), 30),
                                        fromAir(150)),
                                day(3)));
        final var sameTime =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        at(
                                with(
                                        fillBody(
                                                cylinder(7, alice.memberId()),
                                                0,
                                                fromBank(room.he().vesselId(), 40),
                                                fromAir(100)),
                                        "notes",
                                        "same time as the transfer"),
                                day(3)));
        final var voided =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        at(trimixBody(room, alice.memberId()), day(4)));
        voidFill(room.adminEmail(), room.teamId(), voided.fillId(), "duplicate");
        final var team = room.teamId();
        final var admin = room.adminEmail();

        // newest first; the same time by id, newest first; voided hidden
        assertThat(fillIds(listFills(admin, team, "")))
                .containsExactly(
                        sameTime.fillId(), transfer.fillId(), oxygenOnly.fillId(), trimix.fillId());
        assertThat(fillIds(listFills(admin, team, "?includeVoided=false"))).hasSize(4);
        assertThat(fillIds(listFills(admin, team, "?includeVoided=true")))
                .containsExactly(
                        voided.fillId(),
                        sameTime.fillId(),
                        transfer.fillId(),
                        oxygenOnly.fillId(),
                        trimix.fillId());
        // memberId = the charged member
        assertThat(fillIds(listFills(admin, team, "?memberId=" + alice.memberId())))
                .containsExactly(sameTime.fillId(), trimix.fillId());
        assertThat(
                        fillIds(
                                listFills(
                                        admin,
                                        team,
                                        "?memberId=" + alice.memberId() + "&includeVoided=true")))
                .containsExactly(voided.fillId(), sameTime.fillId(), trimix.fillId());
        assertThat(fillIds(listFills(admin, team, "?memberId=" + room.adminMemberId()))).isEmpty();
        // vesselId = a source or the target bank
        assertThat(fillIds(listFills(admin, team, "?vesselId=" + room.o2().vesselId())))
                .containsExactly(transfer.fillId(), oxygenOnly.fillId(), trimix.fillId());
        assertThat(fillIds(listFills(admin, team, "?vesselId=" + room.he().vesselId())))
                .containsExactly(sameTime.fillId(), trimix.fillId());
        assertThat(fillIds(listFills(admin, team, "?vesselId=" + nitrox.vesselId())))
                .containsExactly(transfer.fillId());
        assertThat(
                        fillIds(
                                listFills(
                                        admin,
                                        team,
                                        "?vesselId="
                                                + room.he().vesselId()
                                                + "&memberId="
                                                + alice.memberId())))
                .containsExactly(sameTime.fillId(), trimix.fillId());
        assertThat(
                        fillIds(
                                listFills(
                                        admin,
                                        team,
                                        "?vesselId="
                                                + room.he().vesselId()
                                                + "&memberId="
                                                + bob.memberId())))
                .isEmpty();
    }

    @Test
    void aSummaryShowsBoughtGasOnlyTheTargetLabelAndTheCharge() {
        final var room = room();
        final var diver = addActiveMember(room.adminEmail(), room.teamId());
        final var nitrox = nitroxBank(room);
        final var solved = trimixSolved();
        final var named =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        at(
                                with(
                                        trimixBody(room, diver.memberId()),
                                        "target",
                                        withLabel(diver.memberId(), "Twinset")),
                                day(1)));
        final var plain =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        at(trimixBody(room, diver.memberId()), day(2)));
        final var transfer =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        at(
                                fillBody(
                                        bankTarget(nitrox.vesselId()),
                                        0,
                                        fromBank(room.o2().vesselId(), 30),
                                        fromAir(150)),
                                day(3)));

        final var list = listFills(room.adminEmail(), room.teamId(), "").result();

        assertThat(list)
                .extracting(s -> s.fillId())
                .containsExactly(transfer.fillId(), plain.fillId(), named.fillId());
        final var summary = list.get(2);
        assertThat(summary.targetLabel()).isEqualTo("Twinset");
        assertThat(list.get(1).targetLabel()).isEqualTo("12 L cylinder");
        assertThat(list.get(0).targetLabel()).isEqualTo("EAN32");
        assertThat(summary.chargedMember())
                .isEqualTo(new MemberRef(diver.memberId(), diver.displayName()));
        assertThat(list.get(0).chargedMember()).isNull();
        assertThat(summary.filledAt()).isEqualTo(day(1));
        assertThat(summary.status()).isEqualTo(FillStatus.ACTIVE);
        assertThat(summary.endPressureBar()).isEqualTo(200);
        assertThat(summary.endMix()).isEqualTo(solved.endMix());
        // helium and oxygen bought from the banks; the O2 in the compressor air does not count
        assertThat(summary.heLitres())
                .isCloseTo(solved.stages().get(0).addedHeLitres(), within(1e-9));
        assertThat(summary.o2Litres())
                .isCloseTo(solved.stages().get(1).addedO2Litres(), within(1e-9));
        assertThat(summary.totalCharge())
                .isEqualTo(getFill(room.adminEmail(), room.teamId(), named.fillId()).totalCharge());
        assertThat(list.get(0).heLitres()).isZero();
        assertThat(list.get(0).totalCharge())
                .isEqualTo(
                        getFill(room.adminEmail(), room.teamId(), transfer.fillId()).totalCharge());
    }

    @Test
    void pagesAreZeroBasedAndReportTheTotals() {
        final var room = room();
        final var ids =
                IntStream.rangeClosed(1, 5)
                        .mapToObj(
                                n ->
                                        createFill(
                                                        room.adminEmail(),
                                                        room.teamId(),
                                                        at(
                                                                fillBody(
                                                                        cylinder(
                                                                                12,
                                                                                room
                                                                                        .adminMemberId()),
                                                                        0,
                                                                        fromBank(
                                                                                room.o2()
                                                                                        .vesselId(),
                                                                                20 + n)),
                                                                day(n)))
                                                .fillId())
                        .toList();
        final var newestFirst = ids.reversed();

        final var first = listFills(room.adminEmail(), room.teamId(), "?size=2");
        final var second = listFills(room.adminEmail(), room.teamId(), "?size=2&page=1");
        final var last = listFills(room.adminEmail(), room.teamId(), "?size=2&page=2");
        final var beyond = listFills(room.adminEmail(), room.teamId(), "?size=2&page=3");

        assertThat(fillIds(first)).containsExactlyElementsOf(newestFirst.subList(0, 2));
        assertThat(fillIds(second)).containsExactlyElementsOf(newestFirst.subList(2, 4));
        assertThat(fillIds(last)).containsExactly(newestFirst.get(4));
        assertThat(beyond.result()).isEmpty();
        for (final var page : List.of(first, second, last, beyond)) {
            assertThat(page.size()).isEqualTo(2);
            assertThat(page.totalElements()).isEqualTo(5);
            assertThat(page.totalPages()).isEqualTo(3);
        }
        assertThat(first.page()).isZero();
        assertThat(second.page()).isEqualTo(1);
        assertThat(beyond.page()).isEqualTo(3);
        // defaults and the maximum
        final var all = listFills(room.adminEmail(), room.teamId(), "");
        assertThat(all.size()).isEqualTo(50);
        assertThat(all.page()).isZero();
        assertThat(all.totalPages()).isEqualTo(1);
        assertThat(listFills(room.adminEmail(), room.teamId(), "?size=200").size()).isEqualTo(200);
        assertThat(listFills(room.adminEmail(), room.teamId(), "?size=1").totalPages())
                .isEqualTo(5);
    }

    @Test
    void theListRunsAFixedNumberOfQueriesWhateverTheNumberOfFills() {
        final var room = room();
        final var user = userOf(room.adminEmail());
        final var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            createFill(room.adminEmail(), room.teamId(), trimixBody(room, room.adminMemberId()));
            statistics.clear();
            final var one = fillService.list(room.teamId(), user, null, null, false, 0, 50);
            final var oneFill = statistics.getPrepareStatementCount();

            for (int i = 0; i < 11; i++) {
                createFill(
                        room.adminEmail(), room.teamId(), trimixBody(room, room.adminMemberId()));
            }
            statistics.clear();
            final var twelve = fillService.list(room.teamId(), user, null, null, false, 0, 50);
            final var twelveFills = statistics.getPrepareStatementCount();
            statistics.clear();
            final var pageOfTen = fillService.list(room.teamId(), user, null, null, false, 0, 10);
            final var tenOnAFullPage = statistics.getPrepareStatementCount();

            assertThat(one.result()).hasSize(1);
            assertThat(twelve.result()).hasSize(12);
            assertThat(pageOfTen.result()).hasSize(10);
            // membership, page, stages of the page, then the snapshot's 7 (see D3)
            assertThat(oneFill).isEqualTo(10);
            assertThat(twelveFills).isEqualTo(10);
            // a full page adds the count query
            assertThat(tenOnAFullPage).isEqualTo(11);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void creatingAFillRunsAFixedNumberOfQueriesWhateverTheSizeOfTheTeam() {
        final var room = room();
        final var user = userOf(room.adminEmail());
        final var input =
                new FillInput(
                        day(1),
                        new FillTarget.Cylinder(12, null, room.adminMemberId()),
                        0.0,
                        null,
                        List.of(
                                new FillInput.Stage(
                                        new StageSource.Vessel(room.he().vesselId(), null),
                                        70.0,
                                        150.0),
                                new FillInput.Stage(
                                        new StageSource.Vessel(room.o2().vesselId(), null),
                                        92.0,
                                        null),
                                new FillInput.Stage(new StageSource.Air(), 200.0, null)),
                        null,
                        null);
        final var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            fillService.create(room.teamId(), user, input);
            final var firstCreate = statistics.getPrepareStatementCount();
            for (int i = 0; i < 5; i++) {
                addActiveMember(room.adminEmail(), room.teamId());
                createFill(
                        room.adminEmail(), room.teamId(), trimixBody(room, room.adminMemberId()));
            }
            statistics.clear();
            fillService.create(room.teamId(), user, input);
            final var laterCreate = statistics.getPrepareStatementCount();
            statistics.clear();
            fillService.preview(room.teamId(), user, null, input);
            final var preview = statistics.getPrepareStatementCount();
            statistics.clear();
            fillService.create(room.teamId(), user, withoutGauge(input));
            final var withoutReading = statistics.getPrepareStatementCount();

            assertThat(laterCreate).isEqualTo(firstCreate);
            // membership, team, snapshot 7 = 9 to read; a preview stops there
            assertThat(preview).isEqualTo(9);
            // + the team lock, the two source banks (window re-check under the lock), insert of the
            // fill and its 3 stages
            assertThat(withoutReading).isEqualTo(16);
            // + the He bank looked up again for its gauge, its readings, the gauge reading
            assertThat(laterCreate).isEqualTo(19);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    // --- helpers ------------------------------------------------------------------------

    private static FillInput withoutGauge(final FillInput input) {
        return new FillInput(
                input.filledAt(),
                input.target(),
                input.startPressureBar(),
                input.startMix(),
                input.stages().stream()
                        .map(
                                stage ->
                                        new FillInput.Stage(
                                                stage.source(), stage.pressureAfterBar(), null))
                        .toList(),
                input.endAnalysis(),
                input.notes());
    }

    private GasUser userOf(final String email) {
        final var me = me(email);
        return new GasUser(me.userId(), me.email(), me.displayName(), me.username());
    }

    private static Map<String, Object> withLabel(final long chargedMemberId, final String label) {
        return with(cylinder(12, chargedMemberId), "cylinderLabel", label);
    }

    private VesselDetail nitroxBank(final Room room) {
        return createVessel(
                room.adminEmail(),
                room.teamId(),
                with(
                        with(bank("EAN32", EAN32), "waterVolumeLitres", 100),
                        "openedAt",
                        T0.toEpochMilli()));
    }
}
