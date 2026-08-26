package ch.sthomas.stddivelogger.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.data.repository.DiveComputerRepository;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.CcrUnit;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.entity.CcrUnitEntity;
import ch.sthomas.stddivelogger.model.entity.DiveComputerEntity;
import ch.sthomas.stddivelogger.model.entity.DiveComputerManufacturerEntity;
import ch.sthomas.stddivelogger.model.entity.DiveConfigurationEntity;
import ch.sthomas.stddivelogger.model.entity.DiveEntity;
import ch.sthomas.stddivelogger.model.entity.SuitEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.user.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Covers {@link DiveDataService#inferConfigurationFromComputerIfMissing} - the CCR best-guess
 * applied when a companion profile is attached to an already-existing dive, mirroring the same
 * guard conditions as {@code DiveService#inferConfigurationFromComputer} (used on brand-new dives),
 * which previously only ran on the create path.
 */
@ExtendWith(MockitoExtension.class)
class DiveDataServiceInferConfigurationOnAttachTest {

    @Mock private DiveComputerRepository diveComputerRepository;

    @InjectMocks private DiveDataService diveDataService;

    private static final User USER =
            new User(1, "e@e.ch", "pw", "name", true, Instant.now(), Instant.now(), null, null);

    private static UserEntity userEntity() {
        return new UserEntity(1, "e@e.ch", "pw", "name");
    }

    private static DiveConfigurationEntity emptyConfigurationEntity() {
        final var dive = new DiveEntity();
        final var suit = new SuitEntity(userEntity(), DiveConfiguration.createEmpty(USER).suit());
        return new DiveConfigurationEntity(
                dive,
                suit,
                null,
                DiveConfiguration.createEmpty(USER),
                size -> {
                    throw new UnsupportedOperationException("no cylinders in this fixture");
                });
    }

    private static DiveComputerEntity computerLinkedTo(final CcrUnitEntity ccrUnit) {
        final var computer =
                new DiveComputerEntity(
                        "SN1",
                        "My Computer",
                        new DiveComputerManufacturerEntity("Shearwater"),
                        userEntity());
        computer.setCcrUnit(ccrUnit);
        return computer;
    }

    private static DiveComputerEntity unlinkedComputer() {
        return new DiveComputerEntity(
                "SN1",
                "My Computer",
                new DiveComputerManufacturerEntity("Shearwater"),
                userEntity());
    }

    private static DiveProfileUpload profileOnComputer(final long computerId) {
        return new DiveProfileUpload(computerId, Instant.now(), Instant.now(), List.of());
    }

    @Test
    void fillsInCcrUnitWhenComputerIsLinkedAndConfigurationHasNone() {
        final var ccrUnitEntity =
                new CcrUnitEntity(
                        userEntity(),
                        new CcrUnit(5L, 1L, "rEvo", "", false, BaseConfiguration.SIDEMOUNT_CCR));
        when(diveComputerRepository.findById(42L))
                .thenReturn(Optional.of(computerLinkedTo(ccrUnitEntity)));
        final var configuration = emptyConfigurationEntity();

        diveDataService.inferConfigurationFromComputerIfMissing(
                USER, configuration, profileOnComputer(42L));

        assertEquals(BaseConfiguration.SIDEMOUNT_CCR, configuration.toRecord().base());
        final var resultCcrUnit = configuration.toRecord().ccrUnit();
        assertNotNull(resultCcrUnit);
        assertEquals(5L, resultCcrUnit.id());
    }

    @Test
    void leavesConfigurationUntouchedWhenComputerHasNoLink() {
        when(diveComputerRepository.findById(42L)).thenReturn(Optional.of(unlinkedComputer()));
        final var configuration = emptyConfigurationEntity();

        diveDataService.inferConfigurationFromComputerIfMissing(
                USER, configuration, profileOnComputer(42L));

        assertEquals(BaseConfiguration.OTHER, configuration.toRecord().base());
        assertNull(configuration.toRecord().ccrUnit());
    }

    @Test
    void leavesConfigurationUntouchedWhenUnitHasNoDefaultBase() {
        // A CCR unit exists and is linked, but the diver hasn't confirmed a default base
        // configuration for it yet - nothing safe to guess.
        final var ccrUnitEntity =
                new CcrUnitEntity(userEntity(), new CcrUnit(5L, 1L, "rEvo", "", false, null));
        when(diveComputerRepository.findById(42L))
                .thenReturn(Optional.of(computerLinkedTo(ccrUnitEntity)));
        final var configuration = emptyConfigurationEntity();

        diveDataService.inferConfigurationFromComputerIfMissing(
                USER, configuration, profileOnComputer(42L));

        assertEquals(BaseConfiguration.OTHER, configuration.toRecord().base());
        assertNull(configuration.toRecord().ccrUnit());
    }

    @Test
    void neverOverridesAnAlreadyExplicitCcrUnit() {
        final var existingUnit =
                new CcrUnitEntity(
                        userEntity(),
                        new CcrUnit(
                                9L, 1L, "Existing", "", false, BaseConfiguration.BACKMOUNT_CCR));
        final var dive = new DiveEntity();
        final var suit = new SuitEntity(userEntity(), DiveConfiguration.createEmpty(USER).suit());
        final var explicitConfig =
                new DiveConfiguration(
                        DiveConfiguration.createEmpty(USER).suit(),
                        BaseConfiguration.BACKMOUNT_CCR,
                        null,
                        null,
                        List.of(),
                        existingUnit.toRecord(),
                        null);
        final var configuration =
                new DiveConfigurationEntity(
                        dive,
                        suit,
                        existingUnit,
                        explicitConfig,
                        size -> {
                            throw new UnsupportedOperationException("no cylinders in this fixture");
                        });

        // diveComputerRepository is deliberately never stubbed - the method must return before
        // ever consulting it, since an explicit CCR unit is already present.
        diveDataService.inferConfigurationFromComputerIfMissing(
                USER, configuration, profileOnComputer(42L));

        final var resultCcrUnit = configuration.toRecord().ccrUnit();
        assertNotNull(resultCcrUnit);
        assertEquals(9L, resultCcrUnit.id());
        assertEquals(BaseConfiguration.BACKMOUNT_CCR, configuration.toRecord().base());
    }

    @Test
    void ignoresAUnitBelongingToAnotherUser() {
        // Defense in depth against the computer<->CCR-unit link somehow pointing at a unit that
        // isn't the caller's own.
        final var otherUsersUnit =
                new CcrUnitEntity(
                        new UserEntity(2, "other@e.ch", "pw", "other"),
                        new CcrUnit(
                                5L,
                                2L,
                                "Someone else's rEvo",
                                "",
                                false,
                                BaseConfiguration.SIDEMOUNT_CCR));
        when(diveComputerRepository.findById(42L))
                .thenReturn(Optional.of(computerLinkedTo(otherUsersUnit)));
        final var configuration = emptyConfigurationEntity();

        diveDataService.inferConfigurationFromComputerIfMissing(
                USER, configuration, profileOnComputer(42L));

        assertNull(configuration.toRecord().ccrUnit());
    }
}
