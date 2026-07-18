package ch.sthomas.stddivelogger.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.data.repository.DiveBuddyNameRepository;
import ch.sthomas.stddivelogger.model.entity.DiveBuddyNameEntity;
import ch.sthomas.stddivelogger.model.entity.DiveEntity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class DiveDataServiceRenameBuddyTest {

    @Mock private DiveBuddyNameRepository diveBuddyNameRepository;

    @InjectMocks private DiveDataService diveDataService;

    private static DiveEntity diveWithId(final long id) {
        final var dive = new DiveEntity();
        ReflectionTestUtils.setField(dive, "id", id);
        return dive;
    }

    @Test
    void renamesBuddyOnDivesWithoutConflict() {
        final var diveA = diveWithId(1);
        final var diveB = diveWithId(2);
        final var buddyOnA = new DiveBuddyNameEntity(diveA, "Jon");
        final var buddyOnB = new DiveBuddyNameEntity(diveB, "Jon");

        when(diveBuddyNameRepository.findAllByDive_User_IdAndName(10L, "Jon"))
                .thenReturn(List.of(buddyOnA, buddyOnB));
        when(diveBuddyNameRepository.findAllByDive_User_IdAndName(10L, "Jonathan"))
                .thenReturn(List.of());

        final var count = diveDataService.renameBuddyName(10L, "Jon", "Jonathan");

        assertEquals(2, count);
        assertEquals("Jonathan", buddyOnA.getName());
        assertEquals("Jonathan", buddyOnB.getName());
        verify(diveBuddyNameRepository).saveAll(List.of(buddyOnA, buddyOnB));
        verify(diveBuddyNameRepository, never()).deleteAll(anyList());
    }

    @Test
    void dropsOldNameOnDivesThatAlreadyHaveTheNewName() {
        final var diveA = diveWithId(1);
        final var diveB = diveWithId(2);
        final var buddyOnA = new DiveBuddyNameEntity(diveA, "Jon");
        final var buddyOnB = new DiveBuddyNameEntity(diveB, "Jon");
        final var existingJonathanOnA = new DiveBuddyNameEntity(diveA, "Jonathan");

        when(diveBuddyNameRepository.findAllByDive_User_IdAndName(10L, "Jon"))
                .thenReturn(List.of(buddyOnA, buddyOnB));
        when(diveBuddyNameRepository.findAllByDive_User_IdAndName(10L, "Jonathan"))
                .thenReturn(List.of(existingJonathanOnA));

        final var count = diveDataService.renameBuddyName(10L, "Jon", "Jonathan");

        assertEquals(2, count);
        // Dive A already has "Jonathan" -> the "Jon" row on dive A is dropped, not renamed.
        assertEquals("Jon", buddyOnA.getName());
        verify(diveBuddyNameRepository).deleteAll(List.of(buddyOnA));
        // Dive B has no conflict -> its "Jon" row is renamed in place.
        assertEquals("Jonathan", buddyOnB.getName());
        verify(diveBuddyNameRepository).saveAll(List.of(buddyOnB));
    }

    @Test
    void noOpWhenNamesAreEqualAfterTrimming() {
        final var count = diveDataService.renameBuddyName(10L, " Jon ", "Jon");

        assertEquals(0, count);
        verifyNoInteractions(diveBuddyNameRepository);
    }

    @Test
    void noOpWhenNoMatchingBuddyExists() {
        when(diveBuddyNameRepository.findAllByDive_User_IdAndName(10L, "Ghost"))
                .thenReturn(List.of());

        final var count = diveDataService.renameBuddyName(10L, "Ghost", "Someone");

        assertEquals(0, count);
        verify(diveBuddyNameRepository, never()).saveAll(anyList());
        verify(diveBuddyNameRepository, never()).deleteAll(anyList());
    }
}
