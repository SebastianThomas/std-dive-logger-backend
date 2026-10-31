package ch.sthomas.stddivegastracker.support;

import ch.sthomas.stddivegastracker.calc.fill.FillSolver;
import ch.sthomas.stddivegastracker.calc.fill.FillSolver.StageInput;
import ch.sthomas.stddivegastracker.calc.gas.GasContent;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.calc.gas.VirialFit;
import ch.sthomas.stddivegastracker.model.Page;
import ch.sthomas.stddivegastracker.model.fill.Fill;
import ch.sthomas.stddivegastracker.model.fill.FillPreview;
import ch.sthomas.stddivegastracker.model.fill.FillSummary;
import ch.sthomas.stddivegastracker.model.team.Team;
import ch.sthomas.stddivegastracker.model.vessel.VesselDetail;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fills over HTTP (D4): a ready room (team with line dead volume, one He and one O2 bank), request
 * bodies, and the expectations computed independently with {@code FillSolver} / {@code VirialFit}
 * and the DOMAIN §7.3 open TRUE_UP formula.
 */
public abstract class AbstractFillIntegrationTest extends AbstractVesselIntegrationTest {

    protected static final double DEAD_VOLUME_LITRES = 0.05;
    protected static final MathContext MC = MathContext.DECIMAL128;
    protected static final GasMix EAN32 = new GasMix(0.32, 0);

    @Autowired protected JdbcTemplate jdbc;

    /** An admin's team (dead volume 0.05 L) with a He bank (800.00) and an O2 bank (600.00). */
    protected record Room(String adminEmail, Team team, VesselDetail he, VesselDetail o2) {

        public long teamId() {
            return team.teamId();
        }

        public long adminMemberId() {
            return team.memberId();
        }
    }

    /** Both banks: 50 L at 200 bar, residual 20 bar, TRUE_UP, open since {@link #T0}. */
    protected Room room() {
        final var admin = uniqueEmail("admin");
        final var team =
                createTeam(
                        admin,
                        Map.of(
                                "name",
                                "Team",
                                "defaultLossPolicy",
                                "TRUE_UP",
                                "lineDeadVolumeLitres",
                                DEAD_VOLUME_LITRES));
        final var helium = bank("He bank", GasMix.HELIUM);
        helium.put("initialPressureBar", 200);
        helium.put("expectedResidualPressureBar", 20);
        helium.put("openedAt", T0.toEpochMilli());
        helium.put(
                "initialCostLines",
                List.of(Map.of("payerMemberId", team.memberId(), "amount", "800.00")));
        return new Room(
                admin,
                team,
                createVessel(admin, team.teamId(), helium),
                createO2Bank(admin, team.teamId(), team.memberId(), "TRUE_UP"));
    }

    protected static Instant day(final int daysAfterT0) {
        return T0.plus(Duration.ofDays(daysAfterT0));
    }

    // --- requests -----------------------------------------------------------------------

    protected static String fillsUri(final long teamId) {
        return "/v1/teams/" + teamId + "/fills";
    }

    protected ResponseSpec fillRequest(
            final HttpMethod method,
            final String email,
            final long teamId,
            final String pathAfterFills,
            final @Nullable Object body) {
        return send(method, fillsUri(teamId) + pathAfterFills, email, body);
    }

    protected Fill createFill(
            final String email, final long teamId, final Map<String, Object> body) {
        return fillOf(fillRequest(HttpMethod.POST, email, teamId, "", body));
    }

    protected Fill getFill(final String email, final long teamId, final long fillId) {
        return fillOf(fillRequest(HttpMethod.GET, email, teamId, "/" + fillId, null));
    }

    protected Fill updateFill(
            final String email,
            final long teamId,
            final long fillId,
            final Map<String, Object> body) {
        return fillOf(fillRequest(HttpMethod.PUT, email, teamId, "/" + fillId, body));
    }

    protected Fill voidFill(
            final String email, final long teamId, final long fillId, final String reason) {
        return fillOf(
                fillRequest(
                        HttpMethod.POST,
                        email,
                        teamId,
                        "/" + fillId + "/void",
                        Map.of("reason", reason)));
    }

    protected FillPreview previewFill(
            final String email, final long teamId, final Map<String, Object> body) {
        return Objects.requireNonNull(
                fillRequest(HttpMethod.POST, email, teamId, "/preview", body)
                        .expectStatus()
                        .isOk()
                        .expectBody(FillPreview.class)
                        .returnResult()
                        .getResponseBody());
    }

    protected Page<FillSummary> listFills(
            final String email, final long teamId, final String query) {
        return Objects.requireNonNull(
                fillRequest(HttpMethod.GET, email, teamId, query, null)
                        .expectStatus()
                        .isOk()
                        .expectBody(new ParameterizedTypeReference<Page<FillSummary>>() {})
                        .returnResult()
                        .getResponseBody());
    }

    protected static List<Long> fillIds(final Page<FillSummary> page) {
        return page.result().stream().map(FillSummary::fillId).toList();
    }

    protected void changeMix(final Room room, final long vesselId, final GasMix mix) {
        detailOf(
                vesselRequest(
                        HttpMethod.PUT,
                        room.adminEmail(),
                        room.teamId(),
                        "/" + vesselId,
                        Map.of("mix", Map.of("o2", mix.o2(), "he", mix.he()))));
    }

    private static Fill fillOf(final ResponseSpec response) {
        return Objects.requireNonNull(
                response.expectStatus()
                        .isOk()
                        .expectBody(Fill.class)
                        .returnResult()
                        .getResponseBody());
    }

    // --- bodies -------------------------------------------------------------------------

    protected static Map<String, Object> cylinder(
            final double waterVolumeLitres, final long chargedMemberId) {
        final var target = new HashMap<String, Object>();
        target.put("type", "CYLINDER");
        target.put("waterVolumeLitres", waterVolumeLitres);
        target.put("chargedMemberId", chargedMemberId);
        return target;
    }

    protected static Map<String, Object> bankTarget(final long vesselId) {
        return Map.of("type", "VESSEL", "vesselId", vesselId);
    }

    protected static Map<String, Object> fromBank(
            final long vesselId, final double pressureAfterBar) {
        final var stage = new HashMap<String, Object>();
        stage.put("source", Map.of("type", "VESSEL", "vesselId", vesselId));
        stage.put("pressureAfterBar", pressureAfterBar);
        return stage;
    }

    protected static Map<String, Object> fromAir(final double pressureAfterBar) {
        return Map.of("source", Map.of("type", "AIR"), "pressureAfterBar", pressureAfterBar);
    }

    /** {@code filledAt} is set separately ({@link #at}); stages are in the order given. */
    @SafeVarargs
    protected static Map<String, Object> fillBody(
            final Map<String, Object> target,
            final double startPressureBar,
            final Map<String, Object>... stages) {
        final var body = new HashMap<String, Object>();
        body.put("target", target);
        body.put("startPressureBar", startPressureBar);
        body.put("stages", List.of(stages));
        return body;
    }

    protected static Map<String, Object> at(final Map<String, Object> body, final Instant at) {
        return with(body, "filledAt", at.toEpochMilli());
    }

    protected static Map<String, Object> with(
            final Map<String, Object> body, final String key, final Object value) {
        final var copy = new HashMap<>(body);
        copy.put(key, value);
        return copy;
    }

    protected static Map<String, Object> mix(final GasMix mix) {
        return Map.of("o2", mix.o2(), "he", mix.he());
    }

    /** A stage that also records the bank's gauge afterwards (becomes a reading). */
    protected static Map<String, Object> withGauge(
            final Map<String, Object> stage, final double sourcePressureAfterBar) {
        return with(stage, "sourcePressureAfterBar", sourcePressureAfterBar);
    }

    /** The usual fill: 12 L, 0 bar air, He to 70, O2 to 92, air to 200 (DOMAIN §11.1). */
    protected static Map<String, Object> trimixBody(final Room room, final long chargedMemberId) {
        return fillBody(
                cylinder(12, chargedMemberId),
                0,
                fromBank(room.he().vesselId(), 70),
                fromBank(room.o2().vesselId(), 92),
                fromAir(200));
    }

    /** The solver's answer for {@link #trimixBody} with the room's banks as they are declared. */
    protected static FillSolver.Result trimixSolved() {
        return solve(
                12,
                0,
                GasMix.AIR,
                new StageInput(GasMix.HELIUM, 70, true),
                new StageInput(GasMix.OXYGEN, 92, true),
                new StageInput(GasMix.AIR, 200, false));
    }

    // --- expectations -------------------------------------------------------------------

    protected static FillSolver.Result solve(
            final double waterVolumeLitres,
            final double startPressureBar,
            final GasMix startMix,
            final StageInput... stages) {
        return FillSolver.solve(
                new FillSolver.Input(
                        waterVolumeLitres, startPressureBar, startMix, List.of(stages), null),
                VirialFit.INSTANCE,
                DEAD_VOLUME_LITRES);
    }

    /**
     * DOMAIN §7.3, open TRUE_UP bank of 50 L bought at {@code initialPressureBar} (200 bar, 20 bar
     * residual): unrounded {@code cost / max(V_in - V_res, D)}.
     */
    protected static BigDecimal openRate(
            final String cost, final GasMix mix, final double drawnLitres) {
        final var vIn = content(200, mix);
        final var vRes = content(20, mix);
        return new BigDecimal(cost).divide(bd(vIn).subtract(bd(vRes), MC).max(bd(drawnLitres)), MC);
    }

    /** The unrounded charge of a draw at {@link #openRate}. */
    protected static BigDecimal charge(
            final String cost, final GasMix mix, final double drawnLitres) {
        return openRate(cost, mix, drawnLitres).multiply(bd(drawnLitres), MC);
    }

    protected static double content(final double pressureBar, final GasMix mix) {
        return GasContent.contentLitres(50, pressureBar, mix, VirialFit.INSTANCE);
    }

    protected static BigDecimal bd(final double value) {
        return BigDecimal.valueOf(value);
    }

    protected static String money(final BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    protected long count(final String sql, final Object... args) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
    }
}
