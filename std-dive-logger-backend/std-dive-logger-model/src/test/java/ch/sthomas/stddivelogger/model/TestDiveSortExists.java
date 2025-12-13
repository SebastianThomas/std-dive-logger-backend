package ch.sthomas.stddivelogger.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ch.sthomas.stddivelogger.model.dive.DiveSortColumn;
import ch.sthomas.stddivelogger.model.entity.DiveEntity;

import org.junit.jupiter.api.Test;

public class TestDiveSortExists {
    @Test
    void testSort() {
        for (final var sortCol : DiveSortColumn.values()) {
            assertDoesNotThrow(
                    () -> assertNotNull(DiveEntity.class.getDeclaredField(sortCol.jpaName())));
        }
    }
}
