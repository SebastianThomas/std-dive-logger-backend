package ch.sthomas.stddivegastracker.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The schema's own rules (pairing, range, unique, FK checks) and shape (tables, FK indexes). A
 * failed statement aborts the Postgres transaction, so every rejected statement is its own test
 * invocation; each asserts the <em>name</em> of the constraint that fired, so a different failure
 * (e.g. a NOT NULL hit by accident) cannot pass for it.
 */
@Transactional
final class SchemaConstraintTest extends AbstractIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

    /** A fixture row addressed by name in the CSV tables below. */
    private record Row(String table, String primaryKey, long id) {}

    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired VesselRepository vessels;
    @Autowired FillRepository fills;
    @Autowired PaymentRepository payments;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    private final Map<String, Row> rows = new HashMap<>();
    private TeamEntity team;
    private TeamMemberEntity alice;
    private TeamMemberEntity bob;
    private VesselEntity heBank;
    private FillEntity cylinderFill;

    /**
     * One valid graph: a team of two, a He bank (cost line, reading from the fill), a nitrox bank,
     * a cylinder fill (He stage + AIR stage), a fill into the nitrox bank and a payment.
     */
    @BeforeEach
    void createValidGraph() {
        team = teams.save(new TeamEntity("Constraint team", "CHF", LossPolicy.TRUE_UP));
        alice =
                members.save(
                        new TeamMemberEntity(
                                team.getId(), userId("d1-c-alice@example.test"), Role.ADMIN, T0));
        bob =
                members.save(
                        new TeamMemberEntity(
                                team.getId(), userId("d1-c-bob@example.test"), Role.MEMBER, T0));
        heBank = vessel("He bank", GasMix.HELIUM);
        final var nitroxBank = vessel("Nitrox bank", new GasMix(0.32, 0));
        heBank.getCostLines()
                .add(new VesselCostLineEntity(alice.getId(), new BigDecimal("600.00"), T0, null));

        cylinderFill = fill(alice, GasMix.AIR);
        cylinderFill.setCylinderTarget(12, "D12", bob.getId());
        cylinderFill
                .getStages()
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
                                null));
        cylinderFill
                .getStages()
                .add(new FillStageEntity(1, null, GasMix.AIR, 100, 100, 20.9, 0, 0, 0, null));
        fills.save(cylinderFill);
        heBank.getReadings()
                .add(new VesselReadingEntity(150, T0, cylinderFill.getId(), bob.getId()));

        final var vesselFill = fill(bob, new GasMix(0.32, 0));
        vesselFill.setVesselTarget(nitroxBank.getId());
        vesselFill
                .getStages()
                .add(new FillStageEntity(0, null, GasMix.AIR, 10, 5, 1, 0, 0, 0, null));
        fills.save(vesselFill);

        final var payment =
                payments.save(
                        new PaymentEntity(
                                team.getId(),
                                bob.getId(),
                                alice.getId(),
                                new BigDecimal("10.00"),
                                T0,
                                null,
                                bob.getId()));
        entityManager.flush();

        rows.put("team", new Row("t_team", "pk_team_id", team.getId()));
        rows.put("member", new Row("t_team_member", "pk_team_member_id", bob.getId()));
        rows.put("user", new Row("t_user", "pk_user_id", Objects.requireNonNull(bob.getUserId())));
        rows.put("vessel", new Row("t_vessel", "pk_vessel_id", heBank.getId()));
        rows.put(
                "costLine",
                new Row(
                        "t_vessel_cost_line",
                        "pk_vessel_cost_line_id",
                        heBank.getCostLines().getFirst().getId()));
        rows.put(
                "reading",
                new Row(
                        "t_vessel_reading",
                        "pk_vessel_reading_id",
                        heBank.getReadings().getFirst().getId()));
        rows.put("cylinderFill", new Row("t_fill", "pk_fill_id", cylinderFill.getId()));
        rows.put("vesselFill", new Row("t_fill", "pk_fill_id", vesselFill.getId()));
        rows.put(
                "vesselStage",
                new Row(
                        "t_fill_stage",
                        "pk_fill_stage_id",
                        cylinderFill.getStages().get(0).getId()));
        rows.put(
                "airStage",
                new Row(
                        "t_fill_stage",
                        "pk_fill_stage_id",
                        cylinderFill.getStages().get(1).getId()));
        rows.put("payment", new Row("t_payment", "pk_payment_id", payment.getId()));
    }

    private Long userId(final String email) {
        final var id = TestJwts.uidOf(email);
        users.upsert(id, null, email);
        return id;
    }

    private VesselEntity vessel(final String label, final GasMix mix) {
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

    private FillEntity fill(final TeamMemberEntity recordedBy, final GasMix endMix) {
        final var fill = new FillEntity(team.getId(), recordedBy.getId());
        fill.setFilledAt(T0);
        fill.setStartPressureBar(0);
        fill.setStartMix(GasMix.AIR);
        fill.setEndPressureBar(100);
        fill.setEndMix(endMix);
        fill.setCalcModelVersion("test");
        return fill;
    }

    private int update(final String row, final String set) {
        final var r = Objects.requireNonNull(rows.get(row));
        return jdbc.update(
                "UPDATE " + r.table() + " SET " + set + " WHERE " + r.primaryKey() + " = ?",
                r.id());
    }

    // --- rejected: pairing, sums, ranges, enums, foreign keys (one UPDATE of a valid row each) ---

    @ParameterizedTest(name = "{0}: {1} -> {2}")
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '"',
            textBlock =
                    """
                    # fill target: vessel xor charged member; a cylinder label needs a water volume
                    cylinderFill | fk_charged_member_id = NULL                    | ck_fill_target
                    cylinderFill | water_volume_litres = NULL, cylinder_label = 'D12' | ck_fill_target
                    cylinderFill | fk_target_vessel_id = (SELECT max(pk_vessel_id) FROM t_vessel) | ck_fill_target
                    vesselFill   | water_volume_litres = 12                       | ck_fill_target
                    vesselFill   | cylinder_label = 'D12'                         | ck_fill_target
                    vesselFill   | fk_charged_member_id = fk_recorded_by_member_id | ck_fill_target
                    # closed vessel: closed_at together with its closing pressure
                    vessel       | closed_at = now()                              | ck_vessel_closed
                    vessel       | closing_pressure_bar = 10                      | ck_vessel_closed
                    vessel       | closed_at = opened_at, closing_pressure_bar = 10 | ck_vessel_closed_after_opened
                    # stage source: AIR (no source vessel) has no draw and no line loss
                    airStage     | draw_litres = 5                                | ck_fill_stage_source
                    airStage     | line_loss_litres = 1                           | ck_fill_stage_source
                    # voided fill: voided_at together with a non-blank reason
                    cylinderFill | voided_at = now()                              | ck_fill_void
                    cylinderFill | void_reason = 'typo'                           | ck_fill_void
                    cylinderFill | voided_at = now(), void_reason = ' '           | t_fill_void_reason_check
                    # analyser reading: both fractions or none
                    cylinderFill | end_analysis_o2 = 0.2                          | ck_fill_end_analysis
                    cylinderFill | end_analysis_o2 = 0.8, end_analysis_he = 0.3   | ck_fill_end_analysis
                    # mix: each fraction in [0, 1] and o2 + he <= 1 + 1e-9
                    vessel       | mix_o2 = 0.8, mix_he = 0.3                     | ck_vessel_mix_sum
                    vessel       | mix_o2 = -0.1                                  | t_vessel_mix_o2_check
                    vessel       | mix_he = -0.1                                  | t_vessel_mix_he_check
                    cylinderFill | start_o2 = 0.9, start_he = 0.2                 | ck_fill_start_mix_sum
                    cylinderFill | end_o2 = 0.9, end_he = 0.2                     | ck_fill_end_mix_sum
                    cylinderFill | end_o2 = -0.1                                  | t_fill_end_o2_check
                    vesselStage  | source_o2 = 0.9, source_he = 0.2               | ck_fill_stage_source_mix_sum
                    vesselStage  | source_he = -0.1                               | t_fill_stage_source_he_check
                    # pressures in [0, 400] bar
                    vessel       | initial_pressure_bar = -1                      | t_vessel_initial_pressure_bar_check
                    vessel       | initial_pressure_bar = 400.5                   | t_vessel_initial_pressure_bar_check
                    vessel       | expected_residual_pressure_bar = -1            | t_vessel_expected_residual_pressure_bar_check
                    vessel       | closed_at = now(), closing_pressure_bar = -1   | t_vessel_closing_pressure_bar_check
                    cylinderFill | start_pressure_bar = -1                        | t_fill_start_pressure_bar_check
                    cylinderFill | end_pressure_bar = 401                         | t_fill_end_pressure_bar_check
                    vesselStage  | pressure_after_bar = -1                        | t_fill_stage_pressure_after_bar_check
                    reading      | pressure_bar = 400.1                           | t_vessel_reading_pressure_bar_check
                    team         | default_residual_pressure_bar = -1             | t_team_default_residual_pressure_bar_check
                    # volumes
                    vessel       | water_volume_litres = 0                        | t_vessel_water_volume_litres_check
                    vessel       | water_volume_litres = 5000.1                   | t_vessel_water_volume_litres_check
                    cylinderFill | water_volume_litres = 0                        | t_fill_water_volume_litres_check
                    team         | line_dead_volume_litres = -0.01                | t_team_line_dead_volume_litres_check
                    vesselStage  | added_litres = -1                              | t_fill_stage_added_litres_check
                    vesselStage  | added_o2_litres = -1                           | t_fill_stage_added_o2_litres_check
                    vesselStage  | added_he_litres = -1                           | t_fill_stage_added_he_litres_check
                    vesselStage  | line_loss_litres = -1                          | t_fill_stage_line_loss_litres_check
                    vesselStage  | draw_litres = -1                               | t_fill_stage_draw_litres_check
                    vesselStage  | stage_index = -1                               | t_fill_stage_stage_index_check
                    # money: cost lines and payments in (0, 1e9), no payment to oneself
                    costLine     | amount = 0                                     | t_vessel_cost_line_amount_check
                    costLine     | amount = 1000000000                            | t_vessel_cost_line_amount_check
                    payment      | amount = 0                                     | t_payment_amount_check
                    payment      | fk_to_member_id = fk_from_member_id            | ck_payment_from_to
                    # enums, currency, text
                    team         | currency = 'chf'                               | t_team_currency_check
                    team         | default_loss_policy = 'FOO'                    | t_team_default_loss_policy_check
                    team         | name = ' '                                     | t_team_name_check
                    member       | role = 'OWNER'                                 | t_team_member_role_check
                    # identity: a member is always a user
                    member       | fk_user_id = NULL                              | fk_user_id
                    vessel       | loss_policy = 'FOO'                            | t_vessel_loss_policy_check
                    vessel       | label = ' '                                    | t_vessel_label_check
                    # foreign keys point at existing rows
                    member       | fk_team_id = -1                                | t_team_member_fk_team_id_fkey
                    member       | fk_user_id = -1                                | t_team_member_fk_user_id_fkey
                    vessel       | fk_team_id = -1                                | t_vessel_fk_team_id_fkey
                    vessel       | fk_created_by_member_id = -1                   | t_vessel_fk_created_by_member_id_fkey
                    costLine     | fk_payer_member_id = -1                        | t_vessel_cost_line_fk_payer_member_id_fkey
                    reading      | fk_fill_id = -1                                | t_vessel_reading_fk_fill_id_fkey
                    reading      | fk_created_by_member_id = -1                   | t_vessel_reading_fk_created_by_member_id_fkey
                    cylinderFill | fk_charged_member_id = -1                      | t_fill_fk_charged_member_id_fkey
                    cylinderFill | fk_recorded_by_member_id = -1                  | t_fill_fk_recorded_by_member_id_fkey
                    vesselFill   | fk_target_vessel_id = -1                       | t_fill_fk_target_vessel_id_fkey
                    vesselStage  | fk_source_vessel_id = -1                       | t_fill_stage_fk_source_vessel_id_fkey
                    payment      | fk_to_member_id = -1                           | t_payment_fk_to_member_id_fkey
                    payment      | fk_created_by_member_id = -1                   | t_payment_fk_created_by_member_id_fkey
                    """)
    void invalidRowIsRejectedByTheNamedConstraint(
            final String row, final String set, final String constraint) {
        assertThatThrownBy(() -> update(row, set))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("\"" + constraint + "\"");
    }

    /** Text columns are as long as the request records allow (TeamInput: 60), not longer. */
    @Test
    void aTeamNameLongerThanTheInputAllowsIsRejected() {
        assertThatThrownBy(() -> update("team", "name = repeat('x', 61)"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("character varying(60)");
    }

    // --- accepted: the edges of the same rules ---

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '"',
            textBlock =
                    """
                    vessel       | mix_o2 = 0.7, mix_he = 0.3000000005
                    vessel       | initial_pressure_bar = 0
                    vessel       | initial_pressure_bar = 400
                    vessel       | water_volume_litres = 5000
                    vessel       | closed_at = now(), closing_pressure_bar = 0
                    team         | name = repeat('x', 60)
                    cylinderFill | voided_at = now(), void_reason = 'typo'
                    cylinderFill | end_analysis_o2 = 0.2, end_analysis_he = 0.3
                    cylinderFill | water_volume_litres = 5000
                    vesselStage  | draw_litres = 5
                    airStage     | draw_litres = 0
                    costLine     | amount = 0.01
                    costLine     | amount = 999999999.99
                    payment      | amount = 0.01
                    team         | currency = 'USD'
                    """)
    void edgeValueIsAccepted(final String row, final String set) {
        assertThat(update(row, set)).isEqualTo(1);
    }

    // --- rejected through the entities and repositories ---

    @Test
    void duplicateMemberIsRejected() {
        assertThatThrownBy(
                        () ->
                                members.saveAndFlush(
                                        new TeamMemberEntity(
                                                team.getId(),
                                                Objects.requireNonNull(bob.getUserId()),
                                                Role.MEMBER,
                                                T0)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("\"uq_team_member_team_user\"");
    }

    @Test
    void aBankGaugeOnAnAirStageIsRejected() {
        cylinderFill
                .getStages()
                .add(new FillStageEntity(2, null, GasMix.AIR, 200, 100, 20.9, 0, 0, 0, 150.0));
        assertThatThrownBy(() -> fills.saveAndFlush(cylinderFill))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("\"ck_fill_stage_gauge_bank_only\"");
    }

    @Test
    void duplicateStageIndexIsRejected() {
        cylinderFill
                .getStages()
                .add(new FillStageEntity(1, null, GasMix.AIR, 100, 100, 20.9, 0, 0, 0, null));
        assertThatThrownBy(() -> fills.saveAndFlush(cylinderFill))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("\"uq_fill_stage_fill_index\"");
    }

    @Test
    void paymentToOneselfIsRejected() {
        assertThatThrownBy(
                        () ->
                                payments.saveAndFlush(
                                        new PaymentEntity(
                                                team.getId(),
                                                alice.getId(),
                                                alice.getId(),
                                                new BigDecimal("5.00"),
                                                T0,
                                                null,
                                                alice.getId())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("\"ck_payment_from_to\"");
    }

    @Test
    void negativePressureIsRejected() {
        assertThatThrownBy(
                        () ->
                                vessels.saveAndFlush(
                                        new VesselEntity(
                                                team.getId(),
                                                alice.getId(),
                                                "Leaky",
                                                50,
                                                GasMix.AIR,
                                                -1,
                                                0,
                                                LossPolicy.TRUE_UP,
                                                T0)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("\"t_vessel_initial_pressure_bar_check\"");
    }

    @Test
    void vesselOfAMissingTeamIsRejected() {
        assertThatThrownBy(
                        () ->
                                vessels.saveAndFlush(
                                        new VesselEntity(
                                                -1L,
                                                alice.getId(),
                                                "Orphan",
                                                50,
                                                GasMix.AIR,
                                                0,
                                                0,
                                                LossPolicy.TRUE_UP,
                                                T0)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("\"t_vessel_fk_team_id_fkey\"");
    }

    @Test
    void lossParticipantsAreUniquePerVessel() {
        final var sql =
                "INSERT INTO t_vessel_loss_participant (fk_vessel_id, fk_member_id) VALUES (?, ?)";
        jdbc.update(sql, heBank.getId(), alice.getId());
        assertThatThrownBy(() -> jdbc.update(sql, heBank.getId(), alice.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("\"t_vessel_loss_participant_pkey\"");
    }

    // --- schema shape ---

    @Test
    void exactlyTheGasTrackerTablesExist() {
        assertThat(
                        jdbc.queryForList(
                                """
                                SELECT table_name FROM information_schema.tables
                                WHERE table_schema = 'public' AND table_name LIKE 't\\_%'
                                """,
                                String.class))
                .containsExactlyInAnyOrder(
                        "t_user",
                        "t_team",
                        "t_team_member",
                        "t_vessel",
                        "t_vessel_cost_line",
                        "t_vessel_reading",
                        "t_vessel_loss_participant",
                        "t_fill",
                        "t_fill_stage",
                        "t_payment",
                        "t_scrubber_canister",
                        "t_scrubber_cost_line",
                        "t_scrubber_use");
    }

    @Test
    void theMigrationsApplyInOrder() {
        assertThat(
                        jdbc.queryForList(
                                "SELECT version FROM flyway_schema_history ORDER BY installed_rank",
                                String.class))
                .containsExactly("1.0.0", "1.0.1", "1.0.2", "1.0.3", "1.0.4", "1.0.5", "1.0.6");
    }

    /** AGENTS.md "Migrations": Postgres creates no index for an FK, so each needs a leading one. */
    @Test
    void everyForeignKeyColumnIsTheLeadingColumnOfAnIndex() {
        assertThat(
                        jdbc.queryForList(
                                """
                                SELECT c.conrelid::regclass::text || '.' || a.attname
                                FROM pg_constraint c
                                JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
                                WHERE c.contype = 'f' AND c.connamespace = 'public'::regnamespace
                                  AND NOT EXISTS (SELECT 1 FROM pg_index i
                                                  WHERE i.indrelid = c.conrelid AND i.indkey[0] = c.conkey[1])
                                """,
                                String.class))
                .isEmpty();
    }

    @Test
    void fillsListIsIndexedByTeamThenNewestFirst() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_fill_team_filled_at'",
                                String.class))
                .contains("t_fill USING btree (fk_team_id, filled_at DESC)");
    }
}
