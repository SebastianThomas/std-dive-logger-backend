package ch.sthomas.stddivegastracker.support;

import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.data.repository.FillRepository;
import ch.sthomas.stddivegastracker.model.entity.FillEntity;
import ch.sthomas.stddivegastracker.model.entity.FillStageEntity;
import ch.sthomas.stddivegastracker.model.team.Member;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.vessel.VesselDetail;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Vessels over HTTP and fills persisted straight through the repository (the fills API is D4), for
 * every test class that needs gas to have moved.
 */
public abstract class AbstractVesselIntegrationTest extends AbstractTeamIntegrationTest {

    /** A fixed past instant: vessels open here, fills and readings follow, "now" comes later. */
    protected static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

    @Autowired protected FillRepository fills;

    protected static String vesselsUri(final long teamId) {
        return "/v1/teams/" + teamId + "/vessels";
    }

    protected ResponseSpec vesselRequest(
            final HttpMethod method,
            final String email,
            final long teamId,
            final String pathAfterVessels,
            final @Nullable Object body) {
        return send(method, vesselsUri(teamId) + pathAfterVessels, email, body);
    }

    protected static VesselDetail detailOf(final ResponseSpec response) {
        return Objects.requireNonNull(
                response.expectStatus()
                        .isOk()
                        .expectBody(VesselDetail.class)
                        .returnResult()
                        .getResponseBody());
    }

    protected VesselDetail createVessel(
            final String email, final long teamId, final Map<String, Object> body) {
        return detailOf(vesselRequest(HttpMethod.POST, email, teamId, "", body));
    }

    protected VesselDetail getVessel(final String email, final long teamId, final long vesselId) {
        return detailOf(vesselRequest(HttpMethod.GET, email, teamId, "/" + vesselId, null));
    }

    /** The smallest valid create body: 50 L, {@code mix}; tests add or override fields. */
    protected static Map<String, Object> bank(final String label, final GasMix mix) {
        final var body = new HashMap<String, Object>();
        body.put("label", label);
        body.put("waterVolumeLitres", 50);
        body.put("mix", Map.of("o2", mix.o2(), "he", mix.he()));
        return body;
    }

    /** The reference bank: 50 L pure O2 at 200 bar, residual 20 bar, one cost line of 600.00. */
    protected VesselDetail createO2Bank(
            final String email,
            final long teamId,
            final long payerMemberId,
            final String lossPolicy) {
        final var body = bank("O2 bank", GasMix.OXYGEN);
        body.put("initialPressureBar", 200);
        body.put("expectedResidualPressureBar", 20);
        body.put("lossPolicy", lossPolicy);
        body.put("openedAt", T0.toEpochMilli());
        body.put("initialCostLine", Map.of("payerMemberId", payerMemberId, "amount", "600.00"));
        return createVessel(email, teamId, body);
    }

    /** A user who has signed in once, so adding them makes them ACTIVE at once. */
    protected Member addActiveMember(final String adminEmail, final long teamId) {
        final var email = uniqueEmail("diver");
        me(email);
        return addMember(adminEmail, teamId, email, Role.MEMBER);
    }

    /** A fill row with the boilerplate columns set; the caller picks the target. */
    protected static FillEntity newFill(
            final long teamId, final long recordedByMemberId, final Instant filledAt) {
        final var fill = new FillEntity(teamId, recordedByMemberId);
        fill.setFilledAt(filledAt);
        fill.setStartPressureBar(0);
        fill.setStartMix(GasMix.AIR);
        fill.setEndPressureBar(200);
        fill.setEndMix(GasMix.AIR);
        fill.setCalcModelVersion("test");
        return fill;
    }

    protected long saveFill(final FillEntity fill, final FillStageEntity... stages) {
        fill.getStages().addAll(List.of(stages));
        return fills.save(fill).getId();
    }

    /** A one-stage fill into a diver's cylinder, drawing {@code litres} from a vessel. */
    protected long cylinderDraw(
            final long teamId,
            final long memberId,
            final long vesselId,
            final GasMix sourceMix,
            final Instant at,
            final double litres,
            final double lineLossLitres,
            final boolean voided) {
        final var fill = newFill(teamId, memberId, at);
        fill.setCylinderTarget(12, null, memberId);
        if (voided) {
            fill.markVoided(Instant.now(), "wrong cylinder");
        }
        return saveFill(fill, drawStage(0, vesselId, sourceMix, litres, lineLossLitres));
    }

    /**
     * A stage drawing {@code litres} (+ {@code lineLossLitres}) from a vessel holding {@code mix}.
     */
    protected static FillStageEntity drawStage(
            final int index,
            final long vesselId,
            final GasMix mix,
            final double litres,
            final double lineLossLitres) {
        return new FillStageEntity(
                index,
                vesselId,
                mix,
                100,
                litres,
                litres * mix.o2(),
                litres * mix.he(),
                lineLossLitres,
                litres + lineLossLitres,
                null);
    }

    /** A compressor stage: adds gas to a vessel target, draws nothing. */
    protected static FillStageEntity airStage(final int index, final double litres) {
        return new FillStageEntity(
                index, null, GasMix.AIR, 200, litres, litres * GasMix.AIR.o2(), 0, 0, 0, null);
    }
}
