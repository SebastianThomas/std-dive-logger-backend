package ch.sthomas.stddivelogger.data.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.data.repository.CcrUnitRepository;
import ch.sthomas.stddivelogger.data.repository.GasMixRepository;
import ch.sthomas.stddivelogger.data.repository.GasRepository;
import ch.sthomas.stddivelogger.data.repository.SuitRepository;
import ch.sthomas.stddivelogger.model.dive.gear.CcrUnit;
import ch.sthomas.stddivelogger.model.dive.gear.Suit;
import ch.sthomas.stddivelogger.model.dive.gear.SuitType;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.entity.CcrUnitEntity;
import ch.sthomas.stddivelogger.model.entity.SuitEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.entity.gas.GasEntity;
import ch.sthomas.stddivelogger.model.entity.gas.GasMixEntity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Example;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
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
    @Mock private GasRepository gasRepository;
    @Mock private GasMixRepository gasMixRepository;

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

    /**
     * toEntity(Gas) has the same find-or-create shape as findOrCreateSuit/findOrCreateCcrUnit, but
     * t_gas is a shared/global table (not user-scoped) so the lookup is a Query-by-Example rather
     * than a generated finder method. Unlike suit/ccrUnit, more than one existing match is also
     * possible - historical duplicates predating the unique constraint (see
     * V0_3_6__gas_unique_constraint.sql), or rows that differ only in NULL columns Postgres does
     * not treat as equal - and toEntity must pick one deterministically instead of throwing, which
     * is what MoreCollectors.toOptional() used to do.
     */
    @Test
    void toEntityReturnsExistingMatchInsteadOfCreatingDuplicate() {
        final var gas = Gas.AIR;
        final var mix = new GasMixEntity(gas.o2(), gas.n2(), gas.he());
        mix.id = 1L;
        final var existingRow = new GasEntity(gas, mix, null);
        existingRow.id = 5L;

        when(gasMixRepository.findByO2AndN2AndHe(gas.o2(), gas.n2(), gas.he()))
                .thenReturn(Optional.of(mix));
        when(gasRepository.findAll(any(Example.class))).thenReturn(List.of(existingRow));

        final var result = diveDataService.toEntity(gas);

        assertSame(existingRow, result);
        verify(gasRepository, never()).save(any());
    }

    @Test
    void toEntityPicksLowestIdWhenMultipleExistingMatchesAreFound() {
        final var gas = Gas.AIR;
        final var mix = new GasMixEntity(gas.o2(), gas.n2(), gas.he());
        mix.id = 1L;
        final var higherIdMatch = new GasEntity(gas, mix, null);
        higherIdMatch.id = 7L;
        final var lowerIdMatch = new GasEntity(gas, mix, null);
        lowerIdMatch.id = 3L;

        when(gasMixRepository.findByO2AndN2AndHe(gas.o2(), gas.n2(), gas.he()))
                .thenReturn(Optional.of(mix));
        // Order deliberately doesn't match id order - findAll on a table with no ORDER BY offers
        // no ordering guarantee, and this used to throw via MoreCollectors.toOptional() as soon as
        // a second row came back.
        when(gasRepository.findAll(any(Example.class))).thenReturn(List.of(higherIdMatch, lowerIdMatch));

        final var result = diveDataService.toEntity(gas);

        assertSame(lowerIdMatch, result);
        verify(gasRepository, never()).save(any());
    }

    @Test
    void toEntityRecoversFromLostRaceByReReadingTheWinnersRow() {
        final var gas = Gas.AIR;
        final var mix = new GasMixEntity(gas.o2(), gas.n2(), gas.he());
        mix.id = 1L;
        final var winnersRow = new GasEntity(gas, mix, null);
        winnersRow.id = 5L;

        when(gasMixRepository.findByO2AndN2AndHe(gas.o2(), gas.n2(), gas.he()))
                .thenReturn(Optional.of(mix));
        when(gasRepository.findAll(any(Example.class)))
                // First call (before the insert attempt): not found yet.
                .thenReturn(List.of())
                // Second call (after losing the race): the concurrent request's row is now there.
                .thenReturn(List.of(winnersRow));
        when(gasRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        final var result = diveDataService.toEntity(gas);

        assertSame(winnersRow, result);
    }
}
