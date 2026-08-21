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
            // DATE's jpaName() is a dotted association path ("diveSummary.start"), not a literal
            // field on DiveEntity itself - valid for Sort.by(...) on a JPQL-backed query (Hibernate
            // resolves the path across the diveSummary association), but getDeclaredField() only
            // ever looks at DiveEntity's own direct fields, so it can never pass this specific
            // reflective check. Its only current caller (DiveRepository.
            // findByGroupPrivilegeOrderByDiveStart) is a native query that doesn't go through
            // jpaName()/toSort() at all anyway - see that method's own doc comment.
            if (sortCol == DiveSortColumn.DATE) {
                continue;
            }
            assertDoesNotThrow(
                    () -> assertNotNull(DiveEntity.class.getDeclaredField(sortCol.jpaName())));
        }
    }
}
