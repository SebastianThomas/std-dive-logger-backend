package ch.sthomas.stddivegastracker.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.data.repository.FillRepository;
import ch.sthomas.stddivegastracker.data.repository.PaymentRepository;
import ch.sthomas.stddivegastracker.data.repository.TeamMemberRepository;
import ch.sthomas.stddivegastracker.data.repository.TeamRepository;
import ch.sthomas.stddivegastracker.data.repository.UserRepository;
import ch.sthomas.stddivegastracker.data.repository.VesselRepository;
import ch.sthomas.stddivegastracker.model.entity.FillEntity;
import ch.sthomas.stddivegastracker.model.entity.FillStageEntity;
import ch.sthomas.stddivegastracker.model.entity.PaymentEntity;
import ch.sthomas.stddivegastracker.model.entity.TeamEntity;
import ch.sthomas.stddivegastracker.model.entity.TeamMemberEntity;
import ch.sthomas.stddivegastracker.model.entity.VesselCostLineEntity;
import ch.sthomas.stddivegastracker.model.entity.VesselEntity;
import ch.sthomas.stddivegastracker.model.entity.VesselReadingEntity;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.support.AbstractIntegrationTest;
import ch.sthomas.stddivegastracker.support.TestJwts;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;

/**
 * Each aggregate is saved, flushed, cleared and reloaded, children included. Whole-second instants
 * round-trip exactly (Postgres keeps microseconds). The test context starting is the proof that
 * Flyway migrated and Hibernate validated the schema ({@code ddl-auto=validate}).
 */
@Transactional
final class PersistenceRoundTripTest extends AbstractIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired VesselRepository vessels;
    @Autowired FillRepository fills;
    @Autowired PaymentRepository payments;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    private TeamEntity team;
    private TeamMemberEntity alice;
    private TeamMemberEntity bob;

    @BeforeEach
    void createTeamWithTwoMembers() {
        team = teams.save(new TeamEntity("Round trip team", "CHF", LossPolicy.TRUE_UP));
        alice =
                members.save(
                        new TeamMemberEntity(
                                team.getId(), userId("d1-rt-alice@example.test"), Role.ADMIN, T0));
        bob =
                members.save(
                        new TeamMemberEntity(
                                team.getId(), userId("d1-rt-bob@example.test"), Role.MEMBER, T0));
    }

    private Long userId(final String email) {
        final var id = TestJwts.uidOf(email);
        users.upsert(id, null, email);
        return id;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    /** Rows of {@code table} owned by one parent: other test classes commit rows to the same DB. */
    private int count(final String table, final String parentColumn, final Long parentId) {
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE " + parentColumn + " = ?",
                        Integer.class,
                        parentId));
    }

    private VesselEntity newVessel(final String label, final GasMix mix) {
        return vessels.save(
                new VesselEntity(
                        team.getId(),
                        alice.getId(),
                        label,
                        50,
                        mix,
                        200,
                        20,
                        LossPolicy.POOLED,
                        T0));
    }

    @Test
    void teamKeepsItsSettings() {
        final var created = new TeamEntity("Zurich blenders", "EUR", LossPolicy.POOLED);
        created.setLineDeadVolumeLitres(0.05);
        created.setDefaultResidualPressureBar(20);
        final var id = teams.save(created).getId();
        flushAndClear();

        final var team = teams.findById(id).orElseThrow();
        assertThat(team.getName()).isEqualTo("Zurich blenders");
        assertThat(team.getCurrency()).isEqualTo("EUR");
        assertThat(team.getDefaultLossPolicy()).isEqualTo(LossPolicy.POOLED);
        assertThat(team.getLineDeadVolumeLitres()).isEqualTo(0.05);
        assertThat(team.getDefaultResidualPressureBar()).isEqualTo(20);
        assertThat(team.getCreatedAt()).isNotNull();
        assertThat(team.getUpdatedAt()).isNotNull();
    }

    @Test
    void memberIsActiveUntilItLeaves() {
        flushAndClear();
        final var reloaded = members.findById(bob.getId()).orElseThrow();
        assertThat(reloaded.getTeamId()).isEqualTo(team.getId());
        assertThat(reloaded.getUserId()).isEqualTo(bob.getUserId());
        assertThat(reloaded.getRole()).isEqualTo(Role.MEMBER);
        assertThat(reloaded.getJoinedAt()).isEqualTo(T0);
        assertThat(reloaded.isActive()).isTrue();

        reloaded.setRole(Role.ADMIN);
        reloaded.setLeftAt(T0.plus(1, ChronoUnit.DAYS));
        flushAndClear();

        final var left = members.findById(bob.getId()).orElseThrow();
        assertThat(left.getRole()).isEqualTo(Role.ADMIN);
        assertThat(left.getLeftAt()).isEqualTo(T0.plus(1, ChronoUnit.DAYS));
        assertThat(left.isActive()).isFalse();
    }

    @Test
    void vesselKeepsItsCostLinesAndReadings() {
        final var vessel = newVessel("He bank", GasMix.HELIUM);
        vessel.setNotes("rented");
        vessel.getCostLines()
                .add(new VesselCostLineEntity(alice.getId(), new BigDecimal("600.00"), T0, "buy"));
        vessel.getCostLines()
                .add(
                        new VesselCostLineEntity(
                                bob.getId(), new BigDecimal("12.50"), T0.plusSeconds(60), null));
        vessel.getReadings()
                .add(new VesselReadingEntity(180.5, T0.plusSeconds(120), null, bob.getId()));
        vessel.getReadings()
                .add(new VesselReadingEntity(190, T0.plusSeconds(60), null, alice.getId()));
        flushAndClear();

        final var reloaded = vessels.findById(vessel.getId()).orElseThrow();
        assertThat(reloaded.getTeamId()).isEqualTo(team.getId());
        assertThat(reloaded.getCreatedByMemberId()).isEqualTo(alice.getId());
        assertThat(reloaded.getLabel()).isEqualTo("He bank");
        assertThat(reloaded.getWaterVolumeLitres()).isEqualTo(50);
        assertThat(reloaded.getMix()).isEqualTo(GasMix.HELIUM);
        assertThat(reloaded.getInitialPressureBar()).isEqualTo(200);
        assertThat(reloaded.getExpectedResidualPressureBar()).isEqualTo(20);
        assertThat(reloaded.getLossPolicy()).isEqualTo(LossPolicy.POOLED);
        assertThat(reloaded.getOpenedAt()).isEqualTo(T0);
        assertThat(reloaded.getNotes()).isEqualTo("rented");
        assertThat(reloaded.isClosed()).isFalse();
        assertThat(reloaded.getClosedAt()).isNull();
        assertThat(reloaded.getClosingPressureBar()).isNull();
        assertThat(reloaded.getLossParticipantMemberIds()).isEmpty();
        assertThat(reloaded.getCostLines())
                .extracting(
                        VesselCostLineEntity::getPayerMemberId,
                        VesselCostLineEntity::getAmount,
                        VesselCostLineEntity::getPaidAt,
                        VesselCostLineEntity::getNote)
                .containsExactly(
                        tuple(alice.getId(), new BigDecimal("600.00"), T0, "buy"),
                        tuple(bob.getId(), new BigDecimal("12.50"), T0.plusSeconds(60), null));
        // Ordered by measuredAt, not by insertion.
        assertThat(reloaded.getReadings())
                .extracting(
                        VesselReadingEntity::getPressureBar,
                        VesselReadingEntity::getMeasuredAt,
                        VesselReadingEntity::getCreatedByMemberId)
                .containsExactly(
                        tuple(190.0, T0.plusSeconds(60), alice.getId()),
                        tuple(180.5, T0.plusSeconds(120), bob.getId()));

        reloaded.getCostLines().removeFirst();
        flushAndClear();
        assertThat(vessels.findById(vessel.getId()).orElseThrow().getCostLines()).hasSize(1);
        assertThat(count("t_vessel_cost_line", "fk_vessel_id", vessel.getId())).isEqualTo(1);
    }

    @Test
    void closingAVesselStoresPressureAndParticipantsAndReopeningClearsThem() {
        final var vessel = newVessel("O2 bank", GasMix.OXYGEN);
        vessel.close(T0.plus(2, ChronoUnit.DAYS), 15);
        vessel.getLossParticipantMemberIds().addAll(Set.of(alice.getId(), bob.getId()));
        flushAndClear();

        final var closed = vessels.findById(vessel.getId()).orElseThrow();
        assertThat(closed.isClosed()).isTrue();
        assertThat(closed.getClosedAt()).isEqualTo(T0.plus(2, ChronoUnit.DAYS));
        assertThat(closed.getClosingPressureBar()).isEqualTo(15.0);
        assertThat(closed.getLossParticipantMemberIds())
                .containsExactlyInAnyOrder(alice.getId(), bob.getId());

        closed.reopen();
        flushAndClear();

        final var reopened = vessels.findById(vessel.getId()).orElseThrow();
        assertThat(reopened.isClosed()).isFalse();
        assertThat(reopened.getClosingPressureBar()).isNull();
        assertThat(reopened.getLossParticipantMemberIds()).isEmpty();
        assertThat(count("t_vessel_loss_participant", "fk_vessel_id", vessel.getId())).isZero();
    }

    @Test
    void cylinderFillKeepsItsStagesInOrderAndAReadingCanPointAtIt() {
        final var heBank = newVessel("He bank", GasMix.HELIUM);
        final var o2Bank = newVessel("O2 bank", GasMix.OXYGEN);
        final var fill = new FillEntity(team.getId(), bob.getId());
        fill.setFilledAt(T0);
        fill.setCylinderTarget(12, "D12 left", alice.getId());
        fill.setStartPressureBar(0);
        fill.setStartMix(GasMix.AIR);
        fill.setEndPressureBar(200);
        fill.setEndMix(new GasMix(0.222790, 0.348236));
        fill.setEndAnalysis(new GasMix(0.22, 0.35));
        fill.setNotes("trimix 22/35");
        fill.setCalcModelVersion("virial-nist-2026-1");
        // DOMAIN §11.1 numbers, added out of order: the list comes back by stageIndex.
        fill.getStages()
                .add(
                        new FillStageEntity(
                                2, null, GasMix.AIR, 200, 1279.0526, 267.3, 0, 0, 0, null));
        fill.getStages()
                .add(
                        new FillStageEntity(
                                0,
                                heBank.getId(),
                                GasMix.HELIUM,
                                70,
                                829.0155,
                                0,
                                829.0155,
                                3.4542,
                                832.4697,
                                150.0));
        fill.getStages()
                .add(
                        new FillStageEntity(
                                1,
                                o2Bank.getId(),
                                GasMix.OXYGEN,
                                92,
                                260.5477,
                                260.5477,
                                0,
                                4.5398,
                                265.0875,
                                null));
        fills.save(fill);
        heBank.getReadings()
                .add(new VesselReadingEntity(150, T0.plusSeconds(30), fill.getId(), bob.getId()));
        flushAndClear();

        final var reloaded = fills.findById(fill.getId()).orElseThrow();
        assertThat(reloaded.getTeamId()).isEqualTo(team.getId());
        assertThat(reloaded.getRecordedByMemberId()).isEqualTo(bob.getId());
        assertThat(reloaded.getFilledAt()).isEqualTo(T0);
        assertThat(reloaded.isVesselTarget()).isFalse();
        assertThat(reloaded.getTargetVesselId()).isNull();
        assertThat(reloaded.getWaterVolumeLitres()).isEqualTo(12.0);
        assertThat(reloaded.getCylinderLabel()).isEqualTo("D12 left");
        assertThat(reloaded.getChargedMemberId()).isEqualTo(alice.getId());
        assertThat(reloaded.getStartPressureBar()).isZero();
        assertThat(reloaded.getStartMix()).isEqualTo(GasMix.AIR);
        assertThat(reloaded.getEndPressureBar()).isEqualTo(200);
        assertThat(reloaded.getEndMix()).isEqualTo(new GasMix(0.222790, 0.348236));
        assertThat(reloaded.getEndAnalysis()).isEqualTo(new GasMix(0.22, 0.35));
        assertThat(reloaded.getNotes()).isEqualTo("trimix 22/35");
        assertThat(reloaded.getCalcModelVersion()).isEqualTo("virial-nist-2026-1");
        assertThat(reloaded.isVoided()).isFalse();
        assertThat(reloaded.getVoidReason()).isNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(reloaded.getStages())
                .extracting(
                        FillStageEntity::getStageIndex,
                        FillStageEntity::getSourceVesselId,
                        FillStageEntity::getSourceMix,
                        FillStageEntity::getPressureAfterBar,
                        FillStageEntity::getAddedLitres,
                        FillStageEntity::getAddedO2Litres,
                        FillStageEntity::getAddedHeLitres,
                        FillStageEntity::getLineLossLitres,
                        FillStageEntity::getDrawLitres,
                        FillStageEntity::isAir,
                        stage -> stage.toRecord().sourcePressureAfterBar())
                .containsExactly(
                        tuple(
                                0,
                                heBank.getId(),
                                GasMix.HELIUM,
                                70.0,
                                829.0155,
                                0.0,
                                829.0155,
                                3.4542,
                                832.4697,
                                false,
                                150.0),
                        tuple(
                                1,
                                o2Bank.getId(),
                                GasMix.OXYGEN,
                                92.0,
                                260.5477,
                                260.5477,
                                0.0,
                                4.5398,
                                265.0875,
                                false,
                                null),
                        tuple(
                                2,
                                null,
                                GasMix.AIR,
                                200.0,
                                1279.0526,
                                267.3,
                                0.0,
                                0.0,
                                0.0,
                                true,
                                null));
        final var reading = vessels.findById(heBank.getId()).orElseThrow().getReadings().getFirst();
        assertThat(reading.getFillId()).isEqualTo(fill.getId());
        assertThat(reading.getPressureBar()).isEqualTo(150);
    }

    @Test
    void editingAFillReplacesItsStagesAndCanSwitchTheTarget() {
        final var heBank = newVessel("He bank", GasMix.HELIUM);
        final var houseMix = newVessel("Nitrox bank", new GasMix(0.32, 0));
        final var fill = new FillEntity(team.getId(), alice.getId());
        fill.setFilledAt(T0);
        fill.setCylinderTarget(10, null, alice.getId());
        fill.setStartPressureBar(0);
        fill.setStartMix(GasMix.AIR);
        fill.setEndPressureBar(100);
        fill.setEndMix(GasMix.AIR);
        fill.setCalcModelVersion("test");
        fill.getStages()
                .add(new FillStageEntity(0, null, GasMix.AIR, 50, 100, 20.9, 0, 0, 0, null));
        fill.getStages()
                .add(new FillStageEntity(1, null, GasMix.AIR, 100, 100, 20.9, 0, 0, 0, null));
        fills.save(fill);
        flushAndClear();
        assertThat(count("t_fill_stage", "fk_fill_id", fill.getId())).isEqualTo(2);

        // Removed rows are deleted after inserts: flush between clear() and re-adding the
        // same stage indexes (unique fk_fill_id + stage_index).
        final var editing = fills.findById(fill.getId()).orElseThrow();
        editing.getStages().clear();
        entityManager.flush();
        editing.setVesselTarget(houseMix.getId());
        editing.setEndPressureBar(120);
        editing.getStages()
                .add(
                        new FillStageEntity(
                                0, heBank.getId(), GasMix.HELIUM, 120, 5, 0, 5, 0.1, 5.1, null));
        flushAndClear();

        final var edited = fills.findById(fill.getId()).orElseThrow();
        assertThat(edited.isVesselTarget()).isTrue();
        assertThat(edited.getTargetVesselId()).isEqualTo(houseMix.getId());
        assertThat(edited.getWaterVolumeLitres()).isNull();
        assertThat(edited.getCylinderLabel()).isNull();
        assertThat(edited.getChargedMemberId()).isNull();
        assertThat(edited.getEndPressureBar()).isEqualTo(120);
        assertThat(edited.getStages())
                .extracting(FillStageEntity::getStageIndex, FillStageEntity::getSourceVesselId)
                .containsExactly(tuple(0, heBank.getId()));
        assertThat(count("t_fill_stage", "fk_fill_id", fill.getId())).isEqualTo(1);
    }

    @Test
    void voidingAFillStoresTheTimeAndReason() {
        final var fill = new FillEntity(team.getId(), alice.getId());
        fill.setFilledAt(T0);
        fill.setCylinderTarget(10, null, bob.getId());
        fill.setStartPressureBar(0);
        fill.setStartMix(GasMix.AIR);
        fill.setEndPressureBar(10);
        fill.setEndMix(GasMix.AIR);
        fill.setCalcModelVersion("test");
        fill.getStages().add(new FillStageEntity(0, null, GasMix.AIR, 10, 5, 1, 0, 0, 0, null));
        fills.save(fill);
        flushAndClear();

        fills.findById(fill.getId()).orElseThrow().markVoided(T0.plusSeconds(600), "wrong gauge");
        flushAndClear();

        final var voided = fills.findById(fill.getId()).orElseThrow();
        assertThat(voided.isVoided()).isTrue();
        assertThat(voided.getVoidedAt()).isEqualTo(T0.plusSeconds(600));
        assertThat(voided.getVoidReason()).isEqualTo("wrong gauge");
        assertThat(voided.getEndAnalysis()).isNull();
        assertThat(voided.getStages()).hasSize(1);
    }

    @Test
    void paymentKeepsItsAmountAndMembers() {
        final var payment =
                payments.save(
                        new PaymentEntity(
                                team.getId(),
                                bob.getId(),
                                alice.getId(),
                                new BigDecimal("33.30"),
                                T0,
                                null,
                                alice.getId()));
        flushAndClear();

        final var reloaded = payments.findById(payment.getId()).orElseThrow();
        assertThat(reloaded.getTeamId()).isEqualTo(team.getId());
        assertThat(reloaded.getFromMemberId()).isEqualTo(bob.getId());
        assertThat(reloaded.getToMemberId()).isEqualTo(alice.getId());
        assertThat(reloaded.getAmount()).isEqualTo(new BigDecimal("33.30"));
        assertThat(reloaded.getPaidAt()).isEqualTo(T0);
        assertThat(reloaded.getNote()).isNull();
        assertThat(reloaded.getCreatedByMemberId()).isEqualTo(alice.getId());
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }
}
