package ch.sthomas.stddivegastracker.calc.allocation;

import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.fill;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.input;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.paid;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.transfer;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.vessel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.CostLine;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Vessel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class AllocationInputValidationTest {

    private static Vessel simpleVessel(final long id) {
        return vessel(id, LossPolicy.TRUE_UP, VesselStatus.IN_SERVICE, 100, 0, List.of());
    }

    @ParameterizedTest
    @ValueSource(
            doubles = {-1, -1e-9, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void drawLitresMustBeFiniteAndNotNegative(final double litres) {
        assertThatThrownBy(() -> fill(1, 1, litres, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("litres");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, Double.NaN, Double.POSITIVE_INFINITY})
    void vesselInflowAndResidualMustBeFiniteAndNotNegative(final double litres) {
        assertThatThrownBy(
                        () ->
                                vessel(
                                        1,
                                        LossPolicy.POOLED,
                                        VesselStatus.IN_SERVICE,
                                        litres,
                                        0,
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inflowLitres");
        assertThatThrownBy(
                        () ->
                                vessel(
                                        1,
                                        LossPolicy.POOLED,
                                        VesselStatus.IN_SERVICE,
                                        0,
                                        litres,
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("residualLitres");
    }

    @Test
    void zeroLitresAndZeroAmountsAreValid() {
        assertThatCode(
                        () ->
                                input(
                                        List.of(
                                                vessel(
                                                        1,
                                                        LossPolicy.POOLED,
                                                        VesselStatus.IN_SERVICE,
                                                        0,
                                                        0,
                                                        List.of(paid(1, "0")))),
                                        List.of(fill(1, 1, 0, 1))))
                .doesNotThrowAnyException();
    }

    @Test
    void negativeCostLineAmountIsRejected() {
        assertThatThrownBy(() -> new CostLine(1, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void drawIntoItsOwnSourceVesselIsRejected() {
        assertThatThrownBy(() -> transfer(7, 3, 10, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Draw 7");
    }

    @Test
    void duplicateVesselIdsAreRejected() {
        assertThatThrownBy(() -> input(List.of(simpleVessel(1), simpleVessel(1)), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate vessel id 1");
    }

    @Test
    void duplicateDrawIdsAreRejected() {
        assertThatThrownBy(
                        () ->
                                input(
                                        List.of(simpleVessel(1)),
                                        List.of(fill(5, 1, 10, 1), fill(5, 1, 20, 2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate draw id 5");
    }

    @Test
    void drawFromUnknownVesselIsRejected() {
        assertThatThrownBy(() -> input(List.of(simpleVessel(1)), List.of(fill(5, 2, 10, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown vessel 2");
    }

    @Test
    void transferIntoUnknownVesselIsRejected() {
        assertThatThrownBy(() -> input(List.of(simpleVessel(1)), List.of(transfer(5, 1, 10, 9))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown vessel 9");
    }

    @Test
    void duplicateClosingParticipantIsRejected() {
        assertThatThrownBy(
                        () ->
                                vessel(
                                        1,
                                        LossPolicy.POOLED,
                                        VesselStatus.CLOSED,
                                        100,
                                        0,
                                        List.of(),
                                        4L,
                                        4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vessel 1");
    }

    @Test
    void inputIsDefensivelyCopied() {
        final var vessels = new ArrayList<>(List.of(simpleVessel(1)));
        final var active = new HashSet<>(Set.of(1L));
        final var in = new AllocationInput(vessels, List.of(), active);

        vessels.add(simpleVessel(2));
        active.add(2L);

        assertThatThrownBy(() -> in.vessels().add(simpleVessel(3)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(in.vessels()).hasSize(1);
        assertThat(in.activeMemberIds()).containsExactly(1L);
    }
}
