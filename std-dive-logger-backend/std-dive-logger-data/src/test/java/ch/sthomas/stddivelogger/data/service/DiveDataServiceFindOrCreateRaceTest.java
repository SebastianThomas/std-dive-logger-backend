package ch.sthomas.stddivelogger.data.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.data.repository.CcrUnitRepository;
import ch.sthomas.stddivelogger.data.repository.SuitRepository;
import ch.sthomas.stddivelogger.model.dive.gear.CcrUnit;
import ch.sthomas.stddivelogger.model.dive.gear.Suit;
import ch.sthomas.stddivelogger.model.dive.gear.SuitType;
import ch.sthomas.stddivelogger.model.entity.CcrUnitEntity;
import ch.sthomas.stddivelogger.model.entity.SuitEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

/**
 * findOrCreateSuit/findOrCreateCcrUnit each do a SELECT-then-INSERT with no lock between them - a
 * concurrent identical request can slip in between and insert first, so the DB's own unique
 * constraint (see V0_3_5__suit_ccr_unit_unique_constraints.sql) is the actual backstop. These
 * tests simulate losing that race (save() throwing DataIntegrityViolationException, as Spring Data
 * translates the DB's constraint violation) and confirm the loser recovers by re-reading the
 * winner's row instead of propagating the exception as a request failure.
 */
@ExtendWith(MockitoExtension.class)
class DiveDataServiceFindOrCreateRaceTest {

    @Mock private SuitRepository suitRepository;
    @Mock private CcrUnitRepository ccrUnitRepository;

    @InjectMocks private DiveDataService diveDataService;

    private static UserEntity userWithId(final long id) {
        final var user = new UserEntity();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void findOrCreateSuitRecoversFromLostRaceByReReadingTheWinnersRow() {
        final var user = userWithId(10L);
        final var suit = new Suit(null, 10L, SuitType.NEOPRENE, 5.0, "");
        final var winnersRow = new SuitEntity(user, suit);

        when(suitRepository.findByUser_IdAndTypeAndThicknessMMAndAdditionalNotes(
                        10L, SuitType.NEOPRENE, 5.0, ""))
                // First call (before the insert attempt): not found yet.
                .thenReturn(Optional.empty())
                // Second call (after losing the race): the concurrent request's row is now there.
                .thenReturn(Optional.of(winnersRow));
        when(suitRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        final var result = diveDataService.findOrCreateSuit(user, suit);

        assertSame(winnersRow, result);
    }

    @Test
    void findOrCreateCcrUnitRecoversFromLostRaceByReReadingTheWinnersRow() {
        final var user = userWithId(10L);
        final var ccrUnit = new CcrUnit(null, 10L, "JJ-CCR", "", false);
        final var winnersRow = new CcrUnitEntity(user, ccrUnit);

        when(ccrUnitRepository.findByUser_IdAndNameAndAdditionalNotes(10L, "JJ-CCR", ""))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnersRow));
        when(ccrUnitRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        final var result = diveDataService.findOrCreateCcrUnit(user, ccrUnit);

        assertSame(winnersRow, result);
    }
}
