package ch.sthomas.stddivelogger.model.dive;

import org.jspecify.annotations.Nullable;
import jakarta.validation.constraints.NotNull;

import org.hibernate.query.SortDirection;

public record DiveSort(@NotNull DiveSortColumn column, @NotNull SortDirection direction) {
    public static DiveSort ofNullable(
            @Nullable final DiveSortColumn column, @Nullable final SortDirection direction) {
        return new DiveSort(
                column != null ? column : DiveSortColumn.DEFAULT,
                direction != null ? direction : SortDirection.DESCENDING);
    }
}
