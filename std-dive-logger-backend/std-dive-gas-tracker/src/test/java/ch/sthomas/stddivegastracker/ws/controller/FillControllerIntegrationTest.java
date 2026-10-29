package ch.sthomas.stddivegastracker.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ch.sthomas.stddivegastracker.calc.fill.FillSolver;
import ch.sthomas.stddivegastracker.calc.fill.FillSolver.StageInput;
import ch.sthomas.stddivegastracker.calc.gas.GasContent;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.calc.gas.VirialFit;
import ch.sthomas.stddivegastracker.model.fill.FillPreview;
import ch.sthomas.stddivegastracker.model.fill.FillStatus;
import ch.sthomas.stddivegastracker.model.fill.FillTarget;
import ch.sthomas.stddivegastracker.model.fill.StageResult;
import ch.sthomas.stddivegastracker.model.fill.StageSource;
import ch.sthomas.stddivegastracker.model.fill.Warning;
import ch.sthomas.stddivegastracker.model.team.MemberRef;
import ch.sthomas.stddivegastracker.model.vessel.VesselDetail;
import ch.sthomas.stddivegastracker.support.AbstractFillIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** API.md §4: the numbers of preview, create, update and void (expectations from DOMAIN §5-§7). */
final class FillControllerIntegrationTest extends AbstractFillIntegrationTest {

    // --- the usual fill -----------------------------------------------------------------

    @Test
    void theUsualTrimixFillMatchesTheSolverAndChargesEachBankStageAtItsOpenRate() {
        final var room = room();
        final var diver = addActiveMember(room.adminEmail(), room.teamId());
        final var solved = trimixSolved();
        final var heDraw = solved.stages().get(0).drawLitres();
        final var o2Draw = solved.stages().get(1).drawLitres();
        final var heCharge = charge("800.00", GasMix.HELIUM, heDraw);
        final var o2Charge = charge("600.00", GasMix.OXYGEN, o2Draw);

        final var fill =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        at(trimixBody(room, diver.memberId()), day(1)));

        assertThat(fill.fillId()).isPositive();
        assertThat(fill.status()).isEqualTo(FillStatus.ACTIVE);
        assertThat(fill.filledAt()).isEqualTo(day(1));
        assertThat(fill.target()).isEqualTo(new FillTarget.Cylinder(12, null, diver.memberId()));
        assertThat(fill.chargedMember())
                .isEqualTo(new MemberRef(diver.memberId(), diver.displayName()));
        assertThat(fill.recordedBy())
                .isEqualTo(new MemberRef(room.adminMemberId(), nameOf(room.adminEmail())));
        assertThat(fill.startPressureBar()).isZero();
        assertThat(fill.startMix()).isEqualTo(GasMix.AIR);
        assertThat(fill.endPressureBar()).isEqualTo(200);
        assertThat(fill.endMix()).isEqualTo(solved.endMix());
        assertThat(fill.endAnalysis()).isNull();
        assertThat(fill.voidReason()).isNull();
        assertThat(fill.calcModelVersion()).isEqualTo(VirialFit.MODEL_VERSION);
        assertThat(fill.warnings()).isEmpty();
        assertThat(fill.createdAt()).isNotNull();
        assertThat(fill.updatedAt()).isNotNull();

        assertThat(fill.stages()).hasSize(3);
        assertStage(
                fill.stages().get(0),
                new StageSource.Vessel(room.he().vesselId(), "He bank"),
                GasMix.HELIUM,
                70,
                solved.stages().get(0));
        assertStage(
                fill.stages().get(1),
                new StageSource.Vessel(room.o2().vesselId(), "O2 bank"),
                GasMix.OXYGEN,
                92,
                solved.stages().get(1));
        assertStage(
                fill.stages().get(2),
                new StageSource.Air(),
                GasMix.AIR,
                200,
                solved.stages().get(2));
        // DOMAIN §5.5: the dead volume is part of the draw
        assertThat(heDraw).isGreaterThan(solved.stages().get(0).addedLitres());
        assertThat(fill.stages().get(0).lineLossLitres()).isPositive();

        assertThat(fill.stages().get(0).charge().toPlainString()).isEqualTo(money(heCharge));
        assertThat(fill.stages().get(1).charge().toPlainString()).isEqualTo(money(o2Charge));
        assertThat(fill.stages().get(2).charge().toPlainString()).isEqualTo("0.00");
        assertThat(fill.totalCharge().toPlainString()).isEqualTo(money(heCharge.add(o2Charge)));
        // the banks are open TRUE_UP: their prices can still change; air is free and final
        assertThat(fill.stages())
                .extracting(StageResult::isEstimate)
                .containsExactly(true, true, false);

        // the draws moved the banks (DOMAIN §6)
        assertThat(getVessel(room.adminEmail(), room.teamId(), room.he().vesselId()).drawnLitres())
                .isEqualTo(heDraw);
        assertThat(getVessel(room.adminEmail(), room.teamId(), room.o2().vesselId()).drawnLitres())
                .isEqualTo(o2Draw);
        assertThat(getFill(room.adminEmail(), room.teamId(), fill.fillId())).isEqualTo(fill);
    }

    @Test
    void closingABankFixesThePriceOfItsDrawsInTheFill() {
        final var room = room();
        final var fill =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        at(trimixBody(room, room.adminMemberId()), day(1)));
        assertThat(fill.stages().getFirst().isEstimate()).isTrue();

        vesselRequest(
                        HttpMethod.POST,
                        room.adminEmail(),
                        room.teamId(),
                        "/" + room.he().vesselId() + "/close",
                        Map.of("closingPressureBar", 150))
                .expectStatus()
                .isOk();
        final var closed = getFill(room.adminEmail(), room.teamId(), fill.fillId());

        // DOMAIN §7.3: a closed TRUE_UP bank charges its whole cost to its only draw, for good
        assertThat(closed.stages().get(0).charge().toPlainString()).isEqualTo("800.00");
        assertThat(closed.stages().get(0).isEstimate()).isFalse();
        assertThat(closed.stages().get(1).isEstimate()).isTrue();
        assertThat(closed.totalCharge())
                .isEqualTo(new BigDecimal("800.00").add(closed.stages().get(1).charge()));
    }

    @Test
    void theJsonCarriesTheApiFieldsAndOmitsWhatIsAbsent() {
        final var room = room();
        final var fill =
                createFill(
                        room.adminEmail(), room.teamId(), trimixBody(room, room.adminMemberId()));

        final var json =
                Objects.requireNonNull(
                        fillRequest(
                                        HttpMethod.GET,
                                        room.adminEmail(),
                                        room.teamId(),
                                        "/" + fill.fillId(),
                                        null)
                                .expectStatus()
                                .isOk()
                                .expectBody(String.class)
                                .returnResult()
                                .getResponseBody());

        assertThat(json)
                .contains(
                        "\"type\":\"CYLINDER\"",
                        "\"waterVolumeLitres\":12.0",
                        "\"source\":{\"type\":\"VESSEL\",\"vesselId\":"
                                + room.he().vesselId()
                                + ",\"label\":\"He bank\"}",
                        "\"source\":{\"type\":\"AIR\"}",
                        "\"isEstimate\":true",
                        "\"isEstimate\":false",
                        "\"charge\":\"0.00\"",
                        "\"status\":\"ACTIVE\"",
                        "\"warnings\":[]",
                        "\"calcModelVersion\":\"" + VirialFit.MODEL_VERSION + "\"")
                .doesNotContain("voidReason", "endAnalysis", "\"notes\"", "cylinderLabel");
    }

    // --- preview ------------------------------------------------------------------------

    @Test
    void aPreviewReturnsTheNumbersOfTheCreateAndWritesNothing() {
        final var room = room();
        final var diver = addActiveMember(room.adminEmail(), room.teamId());
        final var body =
                fillBody(
                        cylinder(12, diver.memberId()),
                        0,
                        withGauge(fromBank(room.he().vesselId(), 70), 150),
                        fromBank(room.o2().vesselId(), 92),
                        withGauge(fromAir(200), 180)); // ignored: air has no bank
        final var heBefore = getVessel(room.adminEmail(), room.teamId(), room.he().vesselId());
        final var o2Before = getVessel(room.adminEmail(), room.teamId(), room.o2().vesselId());

        final var preview = previewFill(room.adminEmail(), room.teamId(), body);

        assertThat(
                        listFills(room.adminEmail(), room.teamId(), "?includeVoided=true")
                                .totalElements())
                .isZero();
        assertThat(count("SELECT count(*) FROM t_fill WHERE fk_team_id = ?", room.teamId()))
                .isZero();
        assertThat(
                        count(
                                "SELECT count(*) FROM t_fill_stage s JOIN t_fill f ON f.pk_fill_id = s.fk_fill_id WHERE f.fk_team_id = ?",
                                room.teamId()))
                .isZero();
        assertThat(getVessel(room.adminEmail(), room.teamId(), room.he().vesselId()))
                .isEqualTo(heBefore);
        assertThat(getVessel(room.adminEmail(), room.teamId(), room.o2().vesselId()))
                .isEqualTo(o2Before);
        assertThat(heBefore.readings()).isEmpty();

        final var created = createFill(room.adminEmail(), room.teamId(), body);

        assertThat(preview.stages()).isEqualTo(created.stages());
        assertThat(created.stages().get(2).sourcePressureAfterBar()).isNull();
        assertThat(preview.endPressureBar()).isEqualTo(created.endPressureBar());
        assertThat(preview.endMix()).isEqualTo(created.endMix());
        assertThat(preview.totalCharge()).isEqualTo(created.totalCharge());
        assertThat(preview.warnings()).isEqualTo(created.warnings());
        assertThat(preview.totalCharge()).isGreaterThan(BigDecimal.ZERO);
        // only the create wrote: the gauge reading of the He bank
        final var reading =
                getVessel(room.adminEmail(), room.teamId(), room.he().vesselId())
                        .readings()
                        .getFirst();
        assertThat(reading.pressureBar()).isEqualTo(150);
    }

    @Test
    void aPreviewOfAnEditPricesTheFillAsReplacedNotAsAnExtraFill() {
        final var room = room();
        final var diver = addActiveMember(room.adminEmail(), room.teamId());
        // two such fills draw more than the O2 bank has above its residual, so an estimated price
        // depends on the total drawn: counting the edited fill twice would show
        final var created =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        at(
                                fillBody(
                                        cylinder(24, diver.memberId()),
                                        0,
                                        fromBank(room.o2().vesselId(), 200)),
                                day(1)));
        final var edited =
                at(
                        fillBody(
                                cylinder(24, diver.memberId()),
                                0,
                                fromBank(room.o2().vesselId(), 190)),
                        day(1));

        final var asExtra = previewFill(room.adminEmail(), room.teamId(), edited);
        final var asEdit =
                Objects.requireNonNull(
                        fillRequest(
                                        HttpMethod.POST,
                                        room.adminEmail(),
                                        room.teamId(),
                                        "/preview?replacing=" + created.fillId(),
                                        edited)
                                .expectStatus()
                                .isOk()
                                .expectBody(FillPreview.class)
                                .returnResult()
                                .getResponseBody());
        final var updated = updateFill(room.adminEmail(), room.teamId(), created.fillId(), edited);

        assertThat(asEdit.totalCharge()).isEqualTo(updated.totalCharge());
        assertThat(asEdit.stages()).isEqualTo(updated.stages());
        assertThat(asExtra.totalCharge()).isNotEqualTo(updated.totalCharge());
        // another team's fill id is not found
        fillRequest(
                        HttpMethod.POST,
                        room().adminEmail(),
                        room().teamId(),
                        "/preview?replacing=" + created.fillId(),
                        edited)
                .expectStatus()
                .isNotFound();
    }

    // --- update -------------------------------------------------------------------------

    @Test
    void anUpdateReplacesStagesAndReadingsAndKeepsTheMixSnapshotOfAnUnchangedSource() {
        final var room = room();
        final var recorder = addActiveMember(room.adminEmail(), room.teamId());
        final var diver = addActiveMember(room.adminEmail(), room.teamId());
        final var leanHelium = new GasMix(0, 0.9);
        final var he2 =
                createVessel(
                        room.adminEmail(),
                        room.teamId(),
                        with(
                                with(bank("He 2", leanHelium), "initialPressureBar", 200),
                                "openedAt",
                                T0.toEpochMilli()));
        final var created =
                createFill(
                        emailOf(recorder),
                        room.teamId(),
                        at(
                                fillBody(
                                        cylinder(12, diver.memberId()),
                                        0,
                                        withGauge(fromBank(room.he().vesselId(), 70), 150),
                                        fromBank(room.o2().vesselId(), 92),
                                        fromAir(200)),
                                day(1)));
        // the declared mix changes after the fill; the fill keeps what it was recorded with
        changeMix(room, room.he().vesselId(), new GasMix(0, 0.8));

        // same sources: stage 0 keeps its pure-He snapshot, the numbers follow the new pressures
        final var changed =
                updateFill(
                        room.adminEmail(),
                        room.teamId(),
                        created.fillId(),
                        fillBody(
                                cylinder(12, diver.memberId()),
                                0,
                                withGauge(fromBank(room.he().vesselId(), 75), 140),
                                fromBank(room.o2().vesselId(), 95),
                                fromAir(210)));
        final var solved =
                solve(
                        12,
                        0,
                        GasMix.AIR,
                        new StageInput(GasMix.HELIUM, 75, true),
                        new StageInput(GasMix.OXYGEN, 95, true),
                        new StageInput(GasMix.AIR, 210, false));

        assertThat(changed.fillId()).isEqualTo(created.fillId());
        assertThat(changed.recordedBy()).isEqualTo(created.recordedBy());
        assertThat(changed.recordedBy().memberId()).isEqualTo(recorder.memberId());
        assertThat(changed.filledAt()).isEqualTo(day(1));
        assertThat(changed.createdAt()).isEqualTo(created.createdAt());
        assertThat(changed.updatedAt()).isAfterOrEqualTo(created.updatedAt());
        assertThat(changed.stages()).hasSize(3);
        assertThat(changed.stages().get(0).sourceMix()).isEqualTo(GasMix.HELIUM);
        // the gauge comes back as entered, so an edit can send it again (bank stages only)
        assertThat(created.stages().get(0).sourcePressureAfterBar()).isEqualTo(150.0);
        assertThat(created.stages().get(1).sourcePressureAfterBar()).isNull();
        assertThat(changed.stages().get(0).sourcePressureAfterBar()).isEqualTo(140.0);
        for (int i = 0; i < 3; i++) {
            assertThat(changed.stages().get(i).addedLitres())
                    .isEqualTo(solved.stages().get(i).addedLitres());
            assertThat(changed.stages().get(i).drawLitres())
                    .isEqualTo(solved.stages().get(i).drawLitres());
        }
        assertThat(changed.stages().get(0).addedLitres())
                .isNotEqualTo(created.stages().get(0).addedLitres());
        assertThat(changed.endMix()).isEqualTo(solved.endMix());
        assertThat(changed.endPressureBar()).isEqualTo(210);
        assertThat(getFill(room.adminEmail(), room.teamId(), created.fillId())).isEqualTo(changed);
        assertThat(
                        count(
                                "SELECT count(*) FROM t_fill_stage WHERE fk_fill_id = ?",
                                created.fillId()))
                .isEqualTo(3);
        // the gauge reading was replaced (not added), now by the editor
        final var readings =
                getVessel(room.adminEmail(), room.teamId(), room.he().vesselId()).readings();
        assertThat(readings).hasSize(1);
        assertThat(readings.getFirst().pressureBar()).isEqualTo(140);
        assertThat(readings.getFirst().measuredAt()).isEqualTo(day(1));
        assertThat(readings.getFirst().createdBy().memberId()).isEqualTo(room.adminMemberId());

        // another source at stage 0 takes that bank's current declared mix; the O2 stage is gone
        final var swapped =
                updateFill(
                        room.adminEmail(),
                        room.teamId(),
                        created.fillId(),
                        at(
                                fillBody(
                                        cylinder(12, diver.memberId()),
                                        0,
                                        withGauge(fromBank(he2.vesselId(), 70), 120),
                                        fromAir(200)),
                                day(2)));
        final var solvedSwapped =
                solve(
                        12,
                        0,
                        GasMix.AIR,
                        new StageInput(leanHelium, 70, true),
                        new StageInput(GasMix.AIR, 200, false));

        assertThat(swapped.filledAt()).isEqualTo(day(2));
        assertThat(swapped.stages()).hasSize(2);
        assertThat(swapped.stages().getFirst().source())
                .isEqualTo(new StageSource.Vessel(he2.vesselId(), "He 2"));
        assertThat(swapped.stages().getFirst().sourceMix()).isEqualTo(leanHelium);
        assertThat(swapped.stages().getFirst().drawLitres())
                .isEqualTo(solvedSwapped.stages().getFirst().drawLitres());
        assertThat(
                        count(
                                "SELECT count(*) FROM t_fill_stage WHERE fk_fill_id = ?",
                                created.fillId()))
                .isEqualTo(2);
        assertThat(getVessel(room.adminEmail(), room.teamId(), room.he().vesselId()).readings())
                .isEmpty();
        assertThat(getVessel(room.adminEmail(), room.teamId(), room.he().vesselId()).drawnLitres())
                .isZero();
        assertThat(getVessel(room.adminEmail(), room.teamId(), room.o2().vesselId()).drawnLitres())
                .isZero();
        final var he2Detail = getVessel(room.adminEmail(), room.teamId(), he2.vesselId());
        assertThat(he2Detail.drawnLitres()).isEqualTo(swapped.stages().getFirst().drawLitres());
        assertThat(he2Detail.readings())
                .extracting(VesselDetail.Reading::pressureBar)
                .containsExactly(120.0);

        // back to the first bank: its current declared mix now, not the pure He it once had
        final var back =
                updateFill(
                        room.adminEmail(),
                        room.teamId(),
                        created.fillId(),
                        fillBody(
                                cylinder(12, diver.memberId()),
                                0,
                                fromBank(room.he().vesselId(), 70),
                                fromAir(200)));
        assertThat(back.stages().getFirst().sourceMix()).isEqualTo(new GasMix(0, 0.8));
        assertThat(back.filledAt()).isEqualTo(day(2));
    }

    // --- void ---------------------------------------------------------------------------

    @Test
    void voidingTakesTheFillOutOfEverythingAndDeletesItsReadings() {
        final var room = room();
        final var diver = addActiveMember(room.adminEmail(), room.teamId());
        final var kept =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        at(trimixBody(room, diver.memberId()), day(1)));
        final var wrong =
                createFill(
                        emailOf(diver),
                        room.teamId(),
                        at(
                                fillBody(
                                        cylinder(15, diver.memberId()),
                                        0,
                                        withGauge(fromBank(room.he().vesselId(), 50), 100),
                                        fromAir(200)),
                                day(2)));
        final var heBefore = getVessel(room.adminEmail(), room.teamId(), room.he().vesselId());
        assertThat(heBefore.readings()).hasSize(1);
        assertThat(heBefore.drawnLitres())
                .isCloseTo(
                        kept.stages().get(0).drawLitres() + wrong.stages().get(0).drawLitres(),
                        within(1e-9));

        final var voided =
                voidFill(emailOf(diver), room.teamId(), wrong.fillId(), "  wrong cylinder ");

        assertThat(voided.status()).isEqualTo(FillStatus.VOIDED);
        assertThat(voided.voidReason()).isEqualTo("wrong cylinder");
        assertThat(voided.totalCharge().toPlainString()).isEqualTo("0.00");
        assertThat(voided.stages())
                .allSatisfy(
                        stage -> {
                            assertThat(stage.charge().toPlainString()).isEqualTo("0.00");
                            assertThat(stage.isEstimate()).isFalse();
                        });
        // the numbers it computed stay on the record
        assertThat(voided.stages().getFirst().drawLitres())
                .isEqualTo(wrong.stages().getFirst().drawLitres());
        assertThat(getFill(room.adminEmail(), room.teamId(), wrong.fillId())).isEqualTo(voided);

        final var he = getVessel(room.adminEmail(), room.teamId(), room.he().vesselId());
        assertThat(he.drawnLitres()).isEqualTo(kept.stages().get(0).drawLitres());
        assertThat(he.readings()).isEmpty();
        assertThat(fillIds(listFills(room.adminEmail(), room.teamId(), "")))
                .containsExactly(kept.fillId());
        final var all = listFills(room.adminEmail(), room.teamId(), "?includeVoided=true");
        assertThat(fillIds(all)).containsExactly(wrong.fillId(), kept.fillId());
        assertThat(all.result().getFirst().status()).isEqualTo(FillStatus.VOIDED);
        assertThat(all.result().getFirst().totalCharge().toPlainString()).isEqualTo("0.00");
        // the voided fill's draw no longer counts in the kept fill's price either
        assertThat(
                        getFill(room.adminEmail(), room.teamId(), kept.fillId())
                                .stages()
                                .getFirst()
                                .charge()
                                .toPlainString())
                .isEqualTo(money(charge("800.00", GasMix.HELIUM, he.drawnLitres())));
    }

    @Test
    void aVoidedFillCannotBeEditedOrVoidedAgainAndVoidingNeedsAReason() {
        final var room = room();
        final var fill =
                createFill(
                        room.adminEmail(), room.teamId(), trimixBody(room, room.adminMemberId()));
        final var path = "/" + fill.fillId() + "/void";

        for (final var body :
                List.of(
                        Map.of("reason", ""),
                        Map.of("reason", "   "),
                        Map.of("reason", "x".repeat(201)),
                        Map.of("other", "field"))) {
            fillRequest(HttpMethod.POST, room.adminEmail(), room.teamId(), path, body)
                    .expectStatus()
                    .isBadRequest();
        }
        assertThat(getFill(room.adminEmail(), room.teamId(), fill.fillId()).status())
                .isEqualTo(FillStatus.ACTIVE);

        voidFill(room.adminEmail(), room.teamId(), fill.fillId(), "x".repeat(200));

        fillRequest(
                        HttpMethod.POST,
                        room.adminEmail(),
                        room.teamId(),
                        path,
                        Map.of("reason", "again"))
                .expectStatus()
                .isEqualTo(409);
        fillRequest(
                        HttpMethod.PUT,
                        room.adminEmail(),
                        room.teamId(),
                        "/" + fill.fillId(),
                        trimixBody(room, room.adminMemberId()))
                .expectStatus()
                .isEqualTo(409);
        assertThat(getFill(room.adminEmail(), room.teamId(), fill.fillId()).voidReason())
                .isEqualTo("x".repeat(200));
    }

    // --- fills into a bank (DOMAIN §5.6, §7.2) --------------------------------------------

    @Test
    void aTransferIntoAHouseMixBankCostsWhatTheSourceChargesAndGrowsItsInflow() {
        final var room = room();
        final var nitrox = nitroxBank(room, "EAN32");
        final var solved =
                solve(
                        100,
                        0,
                        EAN32,
                        new StageInput(GasMix.OXYGEN, 30, true),
                        new StageInput(GasMix.AIR, 150, false));
        final var draw = solved.stages().get(0).drawLitres();
        final var cost = charge("600.00", GasMix.OXYGEN, draw);

        final var fill =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        at(
                                fillBody(
                                        bankTarget(nitrox.vesselId()),
                                        0,
                                        fromBank(room.o2().vesselId(), 30),
                                        fromAir(150)),
                                day(1)));

        assertThat(fill.target()).isEqualTo(new FillTarget.Vessel(nitrox.vesselId(), "EAN32"));
        assertThat(fill.chargedMember()).isNull();
        assertThat(fill.startMix()).isEqualTo(EAN32);
        assertThat(fill.endMix()).isEqualTo(solved.endMix());
        // no member pays, but the stage shows what the transfer costs (it becomes the bank's cost)
        assertThat(fill.stages().get(0).charge().toPlainString()).isEqualTo(money(cost));
        assertThat(fill.stages().get(0).isEstimate()).isTrue();
        assertThat(fill.stages().get(1).charge().toPlainString()).isEqualTo("0.00");
        assertThat(fill.totalCharge().toPlainString()).isEqualTo(money(cost));

        final var nitroxAfter = getVessel(room.adminEmail(), room.teamId(), nitrox.vesselId());
        final var openLitres = GasContent.contentLitres(100, 0, EAN32, VirialFit.INSTANCE);
        assertThat(nitroxAfter.inflowLitres())
                .isCloseTo(
                        openLitres
                                + solved.stages().get(0).addedLitres()
                                + solved.stages().get(1).addedLitres(),
                        within(1e-9));
        assertThat(nitroxAfter.totalCost().toPlainString()).isEqualTo(money(cost));
        assertThat(nitroxAfter.rateIsEstimate()).isTrue();
        assertThat(nitrox.totalCost().toPlainString()).isEqualTo("0.00");
        assertThat(getVessel(room.adminEmail(), room.teamId(), room.o2().vesselId()).drawnLitres())
                .isEqualTo(draw);
        assertThat(getFill(room.adminEmail(), room.teamId(), fill.fillId())).isEqualTo(fill);
    }

    @Test
    void anAnalysisOnATransferSetsTheTargetsDeclaredMixAndVoidingDoesNotRevertIt() {
        final var room = room();
        final var nitrox = nitroxBank(room, "EAN32");
        final var solved =
                solve(
                        100,
                        0,
                        EAN32,
                        new StageInput(GasMix.OXYGEN, 30, true),
                        new StageInput(GasMix.AIR, 150, false));
        final var analysed = new GasMix(Math.round(solved.endMix().o2() * 1000) / 1000.0, 0);
        final var body =
                at(
                        with(
                                fillBody(
                                        bankTarget(nitrox.vesselId()),
                                        0,
                                        fromBank(room.o2().vesselId(), 30),
                                        fromAir(150)),
                                "endAnalysis",
                                mix(analysed)),
                        day(1));

        // a preview changes nothing, not even the declared mix
        previewFill(room.adminEmail(), room.teamId(), body);
        assertThat(getVessel(room.adminEmail(), room.teamId(), nitrox.vesselId()).mix())
                .isEqualTo(EAN32);

        final var fill = createFill(room.adminEmail(), room.teamId(), body);

        assertThat(fill.endAnalysis()).isEqualTo(analysed);
        assertThat(fill.endMix()).isEqualTo(solved.endMix());
        assertThat(fill.warnings()).isEmpty();
        assertThat(getVessel(room.adminEmail(), room.teamId(), nitrox.vesselId()).mix())
                .isEqualTo(analysed);

        // an update with another analysis sets that; one without leaves the last declared mix
        final var other = new GasMix(analysed.o2() - 0.004, 0);
        updateFill(
                room.adminEmail(),
                room.teamId(),
                fill.fillId(),
                with(body, "endAnalysis", mix(other)));
        assertThat(getVessel(room.adminEmail(), room.teamId(), nitrox.vesselId()).mix())
                .isEqualTo(other);
        final var plain = new HashMap<>(body);
        plain.remove("endAnalysis");
        updateFill(room.adminEmail(), room.teamId(), fill.fillId(), plain);
        voidFill(room.adminEmail(), room.teamId(), fill.fillId(), "test");
        assertThat(getVessel(room.adminEmail(), room.teamId(), nitrox.vesselId()).mix())
                .isEqualTo(other);
    }

    @Test
    void anAnalysisAfterBlendingNeverChangesTheGasTheBankWasRegisteredWith() {
        final var room = room();
        final var full =
                createVessel(
                                room.adminEmail(),
                                room.teamId(),
                                with(
                                        with(bank("Full EAN32", EAN32), "initialPressureBar", 200),
                                        "openedAt",
                                        T0.toEpochMilli()))
                        .vesselId();
        // before anything is blended in, a mix correction corrects the registered gas too
        final var ean36 = new GasMix(0.36, 0);
        changeMix(room, full, ean36);
        assertThat(getVessel(room.adminEmail(), room.teamId(), full).inflowLitres())
                .isCloseTo(content(200, ean36), within(1e-6));

        // He blended in and analysed as trimix: the 200 bar it started with stay EAN36
        final var fill =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        at(
                                with(
                                        fillBody(
                                                bankTarget(full),
                                                200,
                                                fromBank(room.he().vesselId(), 230)),
                                        "endAnalysis",
                                        mix(new GasMix(0.31, 0.12))),
                                day(1)));
        final var added = fill.stages().stream().mapToDouble(StageResult::addedLitres).sum();
        final var inflow = content(200, ean36) + added;
        final var afterAnalysis = getVessel(room.adminEmail(), room.teamId(), full);
        assertThat(afterAnalysis.mix()).isEqualTo(new GasMix(0.31, 0.12));
        assertThat(afterAnalysis.inflowLitres()).isCloseTo(inflow, within(1e-6));

        // ... and so does a later correction of the declared mix
        changeMix(room, full, new GasMix(0.30, 0.13));
        assertThat(getVessel(room.adminEmail(), room.teamId(), full).inflowLitres())
                .isCloseTo(inflow, within(1e-6));
    }

    @Test
    void anEditMayKeepAMemberWhoLeftOrABankClosedSinceButNotPickThem() {
        final var room = room();
        final var diver = addActiveMember(room.adminEmail(), room.teamId());
        final var other = addActiveMember(room.adminEmail(), room.teamId());
        final var o2 = room.o2().vesselId();
        final var diversFill =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        at(fillBody(cylinder(12, diver.memberId()), 0, fromBank(o2, 50)), day(1)));
        final var othersFill =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        at(fillBody(cylinder(12, other.memberId()), 0, fromBank(o2, 40)), day(1)));
        final var nitrox = nitroxBank(room, "EAN32").vesselId();
        final var blend =
                createFill(room.adminEmail(), room.teamId(), at(transfer(nitrox, o2), day(1)));
        send(
                        HttpMethod.POST,
                        "/v1/teams/" + room.teamId() + "/members/me/leave",
                        emailOf(diver),
                        null)
                .expectStatus()
                .isNoContent();
        vesselRequest(
                        HttpMethod.POST,
                        room.adminEmail(),
                        room.teamId(),
                        "/" + nitrox + "/close",
                        Map.of("closingPressureBar", 100, "closedAt", day(2).toEpochMilli()))
                .expectStatus()
                .isOk();

        // a typo in the pressure is still fixable on both
        final var fixed =
                updateFill(
                        room.adminEmail(),
                        room.teamId(),
                        diversFill.fillId(),
                        at(fillBody(cylinder(12, diver.memberId()), 0, fromBank(o2, 55)), day(1)));
        assertThat(fixed.endPressureBar()).isEqualTo(55);
        updateFill(
                room.adminEmail(),
                room.teamId(),
                blend.fillId(),
                at(fillBody(bankTarget(nitrox), 0, fromBank(o2, 32), fromAir(150)), day(1)));

        // but nobody new is charged to the member who left, nothing new goes into the closed bank,
        // and the kept transfer stays before the closing
        fillRequest(
                        HttpMethod.PUT,
                        room.adminEmail(),
                        room.teamId(),
                        "/" + othersFill.fillId(),
                        at(fillBody(cylinder(12, diver.memberId()), 0, fromBank(o2, 40)), day(1)))
                .expectStatus()
                .isBadRequest();
        fillRequest(
                        HttpMethod.POST,
                        room.adminEmail(),
                        room.teamId(),
                        "",
                        at(transfer(nitrox, o2), day(1)))
                .expectStatus()
                .isEqualTo(409);
        fillRequest(
                        HttpMethod.PUT,
                        room.adminEmail(),
                        room.teamId(),
                        "/" + blend.fillId(),
                        at(transfer(nitrox, o2), day(3)))
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void gasCannotMoveInALoopBetweenBanksButVoidedTransfersDoNotCount() {
        final var room = room();
        final var nitrox = nitroxBank(room, "EAN32");
        final var nitrox2 = nitroxBank(room, "EAN28");
        final var o2ToNitrox =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        at(transfer(nitrox.vesselId(), room.o2().vesselId()), day(1)));
        createFill(
                room.adminEmail(),
                room.teamId(),
                at(transfer(nitrox2.vesselId(), nitrox.vesselId()), day(2)));

        // itself, directly back, and round the chain O2 -> EAN32 -> EAN28 -> O2
        for (final var loop :
                List.of(
                        transfer(nitrox.vesselId(), nitrox.vesselId()),
                        transfer(room.o2().vesselId(), nitrox.vesselId()),
                        transfer(room.o2().vesselId(), nitrox2.vesselId()),
                        transfer(nitrox.vesselId(), nitrox2.vesselId()))) {
            final var response =
                    fillRequest(
                            HttpMethod.POST,
                            room.adminEmail(),
                            room.teamId(),
                            "",
                            at(loop, day(3)));
            assertThat(
                            Objects.requireNonNull(
                                    response.expectStatus()
                                            .isBadRequest()
                                            .expectBody(String.class)
                                            .returnResult()
                                            .getResponseBody()))
                    .contains("This would move gas in a loop between banks.");
        }
        assertThat(listFills(room.adminEmail(), room.teamId(), "").totalElements()).isEqualTo(2);
        // the same direction as before is fine, with an unrelated bank too
        createFill(
                room.adminEmail(),
                room.teamId(),
                at(transfer(nitrox2.vesselId(), room.o2().vesselId()), day(3)));

        // a voided transfer is no edge any more: EAN32 -> O2 is allowed once O2 -> EAN32 is void
        voidFill(room.adminEmail(), room.teamId(), o2ToNitrox.fillId(), "wrong bank");
        createFill(
                room.adminEmail(),
                room.teamId(),
                at(transfer(room.o2().vesselId(), nitrox.vesselId()), day(4)));
    }

    @Test
    void updatingATransferMayReverseItsOwnDirection() {
        final var room = room();
        final var nitrox = nitroxBank(room, "EAN32");
        final var fill =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        at(transfer(nitrox.vesselId(), room.o2().vesselId()), day(1)));

        // the fill's own old edge (O2 -> EAN32) must not make its reversal a loop
        final var reversed =
                updateFill(
                        room.adminEmail(),
                        room.teamId(),
                        fill.fillId(),
                        at(transfer(room.o2().vesselId(), nitrox.vesselId()), day(1)));

        assertThat(reversed.target())
                .isEqualTo(new FillTarget.Vessel(room.o2().vesselId(), "O2 bank"));
        assertThat(getVessel(room.adminEmail(), room.teamId(), room.o2().vesselId()).inflowLitres())
                .isGreaterThan(content(200, GasMix.OXYGEN));
    }

    @Test
    void aClosedTargetIsAConflictAndASourceMustBeInServiceAtTheTimeOfTheFill() {
        final var room = room();
        final var nitrox = nitroxBank(room, "EAN32");
        final var seasonal =
                createVessel(
                        room.adminEmail(),
                        room.teamId(),
                        with(
                                with(bank("Seasonal O2", GasMix.OXYGEN), "initialPressureBar", 200),
                                "openedAt",
                                day(1).toEpochMilli()));
        vesselRequest(
                        HttpMethod.POST,
                        room.adminEmail(),
                        room.teamId(),
                        "/" + seasonal.vesselId() + "/close",
                        Map.of("closingPressureBar", 5, "closedAt", day(5).toEpochMilli()))
                .expectStatus()
                .isOk();
        final var fromSeasonal =
                fillBody(cylinder(12, room.adminMemberId()), 0, fromBank(seasonal.vesselId(), 50));

        createFill(room.adminEmail(), room.teamId(), at(fromSeasonal, day(2)));
        // before it opened, at the closing time (the end is exclusive) and after it
        for (final var filledAt : List.of(T0, day(5), day(6))) {
            assertThat(
                            Objects.requireNonNull(
                                    fillRequest(
                                                    HttpMethod.POST,
                                                    room.adminEmail(),
                                                    room.teamId(),
                                                    "",
                                                    at(fromSeasonal, filledAt))
                                            .expectStatus()
                                            .isBadRequest()
                                            .expectBody(String.class)
                                            .returnResult()
                                            .getResponseBody()))
                    .contains("That bank was not in service at that time.");
        }
        createFill(room.adminEmail(), room.teamId(), at(fromSeasonal, day(4)));
        // a bank target must have been open at the time of the fill as well
        assertThat(
                        Objects.requireNonNull(
                                fillRequest(
                                                HttpMethod.POST,
                                                room.adminEmail(),
                                                room.teamId(),
                                                "",
                                                at(
                                                        transfer(
                                                                nitroxOpenedLater(room),
                                                                room.o2().vesselId()),
                                                        T0))
                                        .expectStatus()
                                        .isBadRequest()
                                        .expectBody(String.class)
                                        .returnResult()
                                        .getResponseBody()))
                .contains("That bank was not in service at that time.");

        vesselRequest(
                        HttpMethod.POST,
                        room.adminEmail(),
                        room.teamId(),
                        "/" + nitrox.vesselId() + "/close",
                        Map.of("closingPressureBar", 1))
                .expectStatus()
                .isOk();
        assertThat(
                        Objects.requireNonNull(
                                fillRequest(
                                                HttpMethod.POST,
                                                room.adminEmail(),
                                                room.teamId(),
                                                "",
                                                at(
                                                        transfer(
                                                                nitrox.vesselId(),
                                                                room.o2().vesselId()),
                                                        day(1)))
                                        .expectStatus()
                                        .isEqualTo(409)
                                        .expectBody(String.class)
                                        .returnResult()
                                        .getResponseBody()))
                .contains("Bank is closed.");
    }

    private long nitroxOpenedLater(final Room room) {
        return createVessel(
                        room.adminEmail(),
                        room.teamId(),
                        with(bank("Later EAN32", GasMix.AIR), "openedAt", day(1).toEpochMilli()))
                .vesselId();
    }

    // --- warnings (API.md §4 Warning) ------------------------------------------------------

    @Test
    void drawingMoreThanABankIsEstimatedToHoldWarnsWithTheStageIndex() {
        final var room = room();
        final var tiny =
                createVessel(
                        room.adminEmail(),
                        room.teamId(),
                        with(
                                with(
                                        with(
                                                bank("Tiny O2", GasMix.OXYGEN),
                                                "waterVolumeLitres",
                                                1),
                                        "initialPressureBar",
                                        10),
                                "openedAt",
                                T0.toEpochMilli()));
        final var body =
                at(
                        fillBody(
                                cylinder(12, room.adminMemberId()),
                                0,
                                fromBank(room.he().vesselId(), 70),
                                fromBank(tiny.vesselId(), 92),
                                fromAir(200)),
                        day(1));
        final var tinyDraw =
                solve(
                                12,
                                0,
                                GasMix.AIR,
                                new StageInput(GasMix.HELIUM, 70, true),
                                new StageInput(GasMix.OXYGEN, 92, true),
                                new StageInput(GasMix.AIR, 200, false))
                        .stages()
                        .get(1)
                        .drawLitres();
        assertThat(tinyDraw).isGreaterThan(tiny.estimatedContentLitres());

        final var preview = previewFill(room.adminEmail(), room.teamId(), body);
        final var created = createFill(room.adminEmail(), room.teamId(), body);

        for (final var warnings : List.of(preview.warnings(), created.warnings())) {
            assertThat(warnings).hasSize(1);
            assertThat(warnings.getFirst().code()).isEqualTo(Warning.Code.SOURCE_OVERDRAWN);
            assertThat(warnings.getFirst().stageIndex()).isEqualTo(1);
            assertThat(warnings.getFirst().message()).contains("Tiny O2");
        }
        // it is saved anyway, and the warning is not stored: a GET shows none
        assertThat(getFill(room.adminEmail(), room.teamId(), created.fillId()).warnings())
                .isEmpty();
    }

    @Test
    void anUpdateMayDrawWhatTheReplacedFillAlreadyTookFromTheBank() {
        final var room = room();
        final var small =
                createVessel(
                        room.adminEmail(),
                        room.teamId(),
                        with(
                                with(
                                        with(
                                                bank("Small O2", GasMix.OXYGEN),
                                                "waterVolumeLitres",
                                                10),
                                        "initialPressureBar",
                                        100),
                                "openedAt",
                                T0.toEpochMilli()));
        final double contentLitres = small.estimatedContentLitres();
        final double lowBar = highestPressureDrawing(0.6 * contentLitres);
        final double highBar = highestPressureDrawing(0.9 * contentLitres);
        assertThat(highBar).isGreaterThan(lowBar);
        final var low =
                fillBody(cylinder(12, room.adminMemberId()), 0, fromBank(small.vesselId(), lowBar));
        final var high =
                fillBody(
                        cylinder(12, room.adminMemberId()), 0, fromBank(small.vesselId(), highBar));
        final var created = createFill(room.adminEmail(), room.teamId(), at(low, day(1)));
        assertThat(created.warnings()).isEmpty();

        // a new fill of that size would take more than what is left; replacing the old one would
        // not
        assertThat(previewFill(room.adminEmail(), room.teamId(), at(high, day(2))).warnings())
                .extracting(Warning::code)
                .containsExactly(Warning.Code.SOURCE_OVERDRAWN);
        assertThat(
                        updateFill(
                                        room.adminEmail(),
                                        room.teamId(),
                                        created.fillId(),
                                        at(high, day(1)))
                                .warnings())
                .isEmpty();
    }

    @Test
    void anAnalysisFurtherThanOnePercentFromTheCalculatedMixWarnsOnCreateAndGet() {
        final var room = room();
        final var solved = trimixSolved();
        final var end = solved.endMix();
        final var close = new GasMix(end.o2() + 0.005, end.he() - 0.005);
        final var farO2 = new GasMix(end.o2() + 0.02, end.he());
        final var farHe = new GasMix(end.o2(), end.he() - 0.015);

        final var accepted =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        with(trimixBody(room, room.adminMemberId()), "endAnalysis", mix(close)));
        assertThat(accepted.warnings()).isEmpty();
        assertThat(accepted.endAnalysis()).isEqualTo(close);

        for (final var analysis : List.of(farO2, farHe)) {
            final var created =
                    createFill(
                            room.adminEmail(),
                            room.teamId(),
                            with(
                                    trimixBody(room, room.adminMemberId()),
                                    "endAnalysis",
                                    mix(analysis)));
            assertThat(created.warnings()).hasSize(1);
            assertThat(created.warnings().getFirst().code())
                    .isEqualTo(Warning.Code.ANALYSIS_MISMATCH);
            assertThat(created.warnings().getFirst().stageIndex()).isNull();
            // saved anyway, and recomputed on every read from the stored mixes
            assertThat(getFill(room.adminEmail(), room.teamId(), created.fillId()).warnings())
                    .isEqualTo(created.warnings());
        }
        assertThat(getFill(room.adminEmail(), room.teamId(), accepted.fillId()).warnings())
                .isEmpty();
    }

    // --- helpers ------------------------------------------------------------------------

    /** 100 L nitrox bank, empty (0 bar), open since T0, no cost of its own. */
    private VesselDetail nitroxBank(final Room room, final String label) {
        return createVessel(
                room.adminEmail(),
                room.teamId(),
                with(
                        with(bank(label, EAN32), "waterVolumeLitres", 100),
                        "openedAt",
                        T0.toEpochMilli()));
    }

    /**
     * O2-style transfer of {@code from} into {@code into}: 0 -> 30 bar from the bank, air to 150.
     */
    private static Map<String, Object> transfer(final long into, final long from) {
        return fillBody(bankTarget(into), 0, fromBank(from, 30), fromAir(150));
    }

    /**
     * The highest whole-bar O2 pressure whose draw into the 12 L cylinder stays within the limit.
     */
    private static double highestPressureDrawing(final double maxLitres) {
        double bar = 1;
        while (solve(12, 0, GasMix.AIR, new StageInput(GasMix.OXYGEN, bar + 1, true))
                        .stages()
                        .getFirst()
                        .drawLitres()
                <= maxLitres) {
            bar++;
        }
        return bar;
    }

    private static void assertStage(
            final StageResult stage,
            final StageSource source,
            final GasMix sourceMix,
            final double pressureAfterBar,
            final FillSolver.StageResult expected) {
        assertThat(stage.source()).isEqualTo(source);
        assertThat(stage.sourceMix()).isEqualTo(sourceMix);
        assertThat(stage.pressureAfterBar()).isEqualTo(pressureAfterBar);
        assertThat(stage.addedLitres()).isEqualTo(expected.addedLitres());
        assertThat(stage.addedO2Litres()).isEqualTo(expected.addedO2Litres());
        assertThat(stage.addedHeLitres()).isEqualTo(expected.addedHeLitres());
        assertThat(stage.lineLossLitres()).isEqualTo(expected.lineLossLitres());
        assertThat(stage.drawLitres()).isEqualTo(expected.drawLitres());
    }

    private static String nameOf(final String email) {
        return email.substring(0, email.indexOf('@'));
    }
}
