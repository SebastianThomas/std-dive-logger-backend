package ch.sthomas.stddivegastracker.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.data.service.FillDataService;
import ch.sthomas.stddivegastracker.model.fill.FillContent;
import ch.sthomas.stddivegastracker.model.fill.FillTarget;
import ch.sthomas.stddivegastracker.model.vessel.VesselDetail;
import ch.sthomas.stddivegastracker.support.AbstractFillIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

/**
 * The write guarantee behind the service's fast loop check (DOMAIN §5.6): the data service refuses
 * a loop on its own, whatever the caller checked before.
 */
final class FillDataServiceIntegrationTest extends AbstractFillIntegrationTest {

    private static final String LOOP = "This would move gas in a loop between banks.";

    @Autowired FillDataService fillDataService;

    @Test
    void createRefusesATransferThatClosesALoopEvenWithoutAnyPreCheck() {
        final var room = room();
        final var a = createVessel(room.adminEmail(), room.teamId(), emptyBank("EAN32"));
        final var b = createVessel(room.adminEmail(), room.teamId(), emptyBank("EAN28"));
        createFill(room.adminEmail(), room.teamId(), at(transfer(a, b), day(1)));
        final var fills = count("SELECT count(*) FROM t_fill WHERE fk_team_id = ?", room.teamId());

        // b -> a is stored; a stale caller now offers a -> b, and a -> a
        for (final var loop : List.of(transferContent(b, a), transferContent(a, a))) {
            assertThatThrownBy(
                            () -> fillDataService.create(room.teamId(), room.adminMemberId(), loop))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(LOOP);
        }
        assertThat(count("SELECT count(*) FROM t_fill WHERE fk_team_id = ?", room.teamId()))
                .isEqualTo(fills);
    }

    @Test
    void updateRefusesALoopButMayReverseTheFillsOwnTransfer() {
        final var room = room();
        final var a = createVessel(room.adminEmail(), room.teamId(), emptyBank("EAN32"));
        final var b = createVessel(room.adminEmail(), room.teamId(), emptyBank("EAN28"));
        final var c = createVessel(room.adminEmail(), room.teamId(), emptyBank("EAN36"));
        final var bToA = createFill(room.adminEmail(), room.teamId(), at(transfer(a, b), day(1)));
        final var cToB = createFill(room.adminEmail(), room.teamId(), at(transfer(b, c), day(2)));

        // the second fill (c -> b), edited to feed b from a, would close a loop with the first
        // one (b -> a)
        assertThatThrownBy(
                        () ->
                                fillDataService.update(
                                        room.teamId(),
                                        cToB.fillId(),
                                        room.adminMemberId(),
                                        transferContent(b, a)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(LOOP);
        // unchanged (the create response also carried a SOURCE_OVERDRAWN warning, a GET does not)
        final var unchanged = getFill(room.adminEmail(), room.teamId(), cToB.fillId());
        assertThat(unchanged.target()).isEqualTo(cToB.target());
        assertThat(unchanged.stages()).isEqualTo(cToB.stages());

        // the first fill's own old edge b -> a is not an obstacle to reversing it
        final var reversed =
                fillDataService.update(
                        room.teamId(), bToA.fillId(), room.adminMemberId(), transferContent(b, a));
        assertThat(reversed.content().target())
                .isEqualTo(new FillTarget.Vessel(b.vesselId(), null));
    }

    // --- helpers ------------------------------------------------------------------------

    private static Map<String, Object> emptyBank(final String label) {
        return with(
                with(bank(label, EAN32), "waterVolumeLitres", 100), "openedAt", T0.toEpochMilli());
    }

    /** Body: 0 -> 30 bar from {@code from}, air to 150, into {@code into}. */
    private static Map<String, Object> transfer(final VesselDetail into, final VesselDetail from) {
        return fillBody(
                bankTarget(into.vesselId()), 0, fromBank(from.vesselId(), 30), fromAir(150));
    }

    /** What the service would hand over for {@link #transfer}: a hand-built, unsolved content. */
    private static FillContent transferContent(final VesselDetail into, final VesselDetail from) {
        return new FillContent(
                day(3),
                new FillTarget.Vessel(into.vesselId(), null),
                0,
                EAN32,
                150,
                GasMix.AIR,
                null,
                null,
                "test",
                List.of(
                        new FillContent.Stage(
                                -1, 0, from.vesselId(), EAN32, 30, 100, 32, 0, 0, 100, null),
                        new FillContent.Stage(
                                -2, 1, null, GasMix.AIR, 150, 100, 20.9, 0, 0, 0, null)));
    }
}
