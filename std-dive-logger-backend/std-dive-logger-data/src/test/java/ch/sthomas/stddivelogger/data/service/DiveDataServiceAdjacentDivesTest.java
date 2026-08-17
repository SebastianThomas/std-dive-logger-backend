package ch.sthomas.stddivelogger.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.model.entity.DiveEntity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class DiveDataServiceAdjacentDivesTest {

    @Mock private DiveRepository diveRepository;

    @InjectMocks private DiveDataService diveDataService;

    private static DiveEntity diveWithIdAndNumber(final long id, final int number) {
        final var dive = new DiveEntity();
        ReflectionTestUtils.setField(dive, "id", id);
        ReflectionTestUtils.setField(dive, "number", number);
        return dive;
    }

    @Test
    void findsBothNeighborsForADiveInTheMiddleOfTheSequence() {
        final var current = diveWithIdAndNumber(20, 5);
        when(diveRepository.findByIdAndUser_Id(20L, 10L)).thenReturn(Optional.of(current));
        when(diveRepository.findPreviousDiveNumber(10L, 5)).thenReturn(Optional.of(4));
        when(diveRepository.findNextDiveNumber(10L, 5)).thenReturn(Optional.of(6));
        when(diveRepository.findByUser_IdAndNumber(10L, 4))
                .thenReturn(Optional.of(diveWithIdAndNumber(19, 4)));
        when(diveRepository.findByUser_IdAndNumber(10L, 6))
                .thenReturn(Optional.of(diveWithIdAndNumber(21, 6)));

        final var result = diveDataService.findAdjacentDives(10L, 20L).orElseThrow();

        assertEquals(19L, result.previousDiveId());
        assertEquals(21L, result.nextDiveId());
    }

    @Test
    void returnsNullPreviousIdForTheFirstDiveInTheSequence() {
        final var current = diveWithIdAndNumber(1, 1);
        when(diveRepository.findByIdAndUser_Id(1L, 10L)).thenReturn(Optional.of(current));
        when(diveRepository.findPreviousDiveNumber(10L, 1)).thenReturn(Optional.empty());
        when(diveRepository.findNextDiveNumber(10L, 1)).thenReturn(Optional.of(2));
        when(diveRepository.findByUser_IdAndNumber(10L, 2))
                .thenReturn(Optional.of(diveWithIdAndNumber(2, 2)));

        final var result = diveDataService.findAdjacentDives(10L, 1L).orElseThrow();

        assertNull(result.previousDiveId());
        assertEquals(2L, result.nextDiveId());
    }

    @Test
    void returnsNullNextIdForTheLastDiveInTheSequence() {
        final var current = diveWithIdAndNumber(9, 9);
        when(diveRepository.findByIdAndUser_Id(9L, 10L)).thenReturn(Optional.of(current));
        when(diveRepository.findPreviousDiveNumber(10L, 9)).thenReturn(Optional.of(8));
        when(diveRepository.findNextDiveNumber(10L, 9)).thenReturn(Optional.empty());
        when(diveRepository.findByUser_IdAndNumber(10L, 8))
                .thenReturn(Optional.of(diveWithIdAndNumber(8, 8)));

        final var result = diveDataService.findAdjacentDives(10L, 9L).orElseThrow();

        assertEquals(8L, result.previousDiveId());
        assertNull(result.nextDiveId());
    }

    @Test
    void returnsEmptyWhenTheDiveDoesNotBelongToThisUser() {
        when(diveRepository.findByIdAndUser_Id(20L, 10L)).thenReturn(Optional.empty());

        final var result = diveDataService.findAdjacentDives(10L, 20L);

        assertTrue(result.isEmpty());
        verifyNoMoreInteractions(diveRepository);
    }
}
