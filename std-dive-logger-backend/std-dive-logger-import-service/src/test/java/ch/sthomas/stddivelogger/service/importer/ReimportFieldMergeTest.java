package ch.sthomas.stddivelogger.service.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.model.controller.dive.upload.ReimportResolution;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.conditions.VisibilityFeeling;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;

import org.junit.jupiter.api.Test;

import java.util.List;

class ReimportFieldMergeTest {

    // --- notes: empty-vs-empty, empty-vs-filled, same, conflict -----------------------------

    @Test
    void notesAutoFillsWhenExistingIsBlank() {
        assertThat(ReimportFieldMerge.resolveNotes(null, "new notes", null)).isEqualTo("new notes");
        assertThat(ReimportFieldMerge.resolveNotes("  ", "new notes", null)).isEqualTo("new notes");
    }

    @Test
    void notesKeepsExistingWhenReimportedIsBlank() {
        assertThat(ReimportFieldMerge.resolveNotes("existing", "", null)).isNull();
        assertThat(ReimportFieldMerge.resolveNotes("existing", "   ", null)).isNull();
    }

    @Test
    void notesIsNoOpWhenIdentical() {
        assertThat(ReimportFieldMerge.resolveNotes("same text", "same text", null)).isNull();
    }

    @Test
    void notesConflictRequiresAChoice() {
        assertThatThrownBy(() -> ReimportFieldMerge.resolveNotes("old", "new", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("notes");

        assertThat(
                        ReimportFieldMerge.resolveNotes(
                                "old", "new", ReimportResolution.Choice.EXISTING))
                .isNull();
        assertThat(ReimportFieldMerge.resolveNotes("old", "new", ReimportResolution.Choice.NEW))
                .isEqualTo("new");
    }

    @Test
    void computeConflictsFlagsOnlyTheConflictingNotesField() {
        final var conflicts =
                ReimportFieldMerge.computeConflicts(
                        "old notes",
                        Visibility.EMPTY,
                        List.of(),
                        DiveGasConsumption.EMPTY,
                        "new notes",
                        Visibility.EMPTY,
                        List.of(),
                        DiveGasConsumption.EMPTY);

        assertThat(conflicts.hasAny()).isTrue();
        final var notesConflict = java.util.Objects.requireNonNull(conflicts.notes());
        assertThat(notesConflict.existing()).isEqualTo("old notes");
        assertThat(notesConflict.reimported()).isEqualTo("new notes");
        assertThat(conflicts.visibility()).isNull();
        assertThat(conflicts.namedBuddies()).isNull();
        assertThat(conflicts.gasConsumption()).isNull();
    }

    @Test
    void computeConflictsIsEmptyWhenNothingDisagrees() {
        final var conflicts =
                ReimportFieldMerge.computeConflicts(
                        null,
                        null,
                        List.of(),
                        null,
                        "",
                        Visibility.EMPTY,
                        List.of(),
                        DiveGasConsumption.EMPTY);

        assertThat(conflicts.hasAny()).isFalse();
    }

    // --- visibility ----------------------------------------------------------------------

    @Test
    void visibilityAutoFillsFromEmptyOrNullExisting() {
        final var newVis = new Visibility(10.0, "clear", VisibilityFeeling.HIGH);
        assertThat(ReimportFieldMerge.resolveVisibility(null, newVis, null)).isEqualTo(newVis);
        assertThat(ReimportFieldMerge.resolveVisibility(Visibility.EMPTY, newVis, null))
                .isEqualTo(newVis);
    }

    @Test
    void visibilityConflictRequiresAChoice() {
        final var existing = new Visibility(5.0, "murky", VisibilityFeeling.LOW);
        final var reimported = new Visibility(20.0, "clear", VisibilityFeeling.HIGH);

        assertThatThrownBy(() -> ReimportFieldMerge.resolveVisibility(existing, reimported, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(
                        ReimportFieldMerge.resolveVisibility(
                                existing, reimported, ReimportResolution.Choice.NEW))
                .isEqualTo(reimported);
    }

    // --- gas consumption -------------------------------------------------------------------

    @Test
    void gasConsumptionAutoFillsFromEmpty() {
        final var newGas = new DiveGasConsumption(20.0, 15.0, 1200.0);
        assertThat(ReimportFieldMerge.resolveGasConsumption(DiveGasConsumption.EMPTY, newGas, null))
                .isEqualTo(newGas);
    }

    @Test
    void gasConsumptionConflictRequiresAChoice() {
        final var existing = new DiveGasConsumption(18.0, 14.0, 1000.0);
        final var reimported = new DiveGasConsumption(20.0, 15.0, 1200.0);

        assertThatThrownBy(
                        () -> ReimportFieldMerge.resolveGasConsumption(existing, reimported, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(
                        ReimportFieldMerge.resolveGasConsumption(
                                existing, reimported, ReimportResolution.Choice.EXISTING))
                .isNull();
    }

    // --- named buddies (list-valued, supports UNION) ----------------------------------------

    @Test
    void namedBuddiesAutoFillsWhenExistingIsEmpty() {
        assertThat(ReimportFieldMerge.resolveNamedBuddies(List.of(), List.of("Alice", "Bob"), null))
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void namedBuddiesIsNoOpWhenSameSetRegardlessOfOrder() {
        assertThat(
                        ReimportFieldMerge.resolveNamedBuddies(
                                List.of("Alice", "Bob"), List.of("Bob", "Alice"), null))
                .isNull();
    }

    @Test
    void namedBuddiesConflictSupportsUnionChoice() {
        final var existing = List.of("Alice");
        final var reimported = List.of("Bob");

        assertThatThrownBy(() -> ReimportFieldMerge.resolveNamedBuddies(existing, reimported, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(
                        ReimportFieldMerge.resolveNamedBuddies(
                                existing, reimported, ReimportResolution.BuddiesChoice.UNION))
                .containsExactlyInAnyOrder("Alice", "Bob");
        assertThat(
                        ReimportFieldMerge.resolveNamedBuddies(
                                existing, reimported, ReimportResolution.BuddiesChoice.EXISTING))
                .isNull();
        assertThat(
                        ReimportFieldMerge.resolveNamedBuddies(
                                existing, reimported, ReimportResolution.BuddiesChoice.NEW))
                .containsExactly("Bob");
    }
}
