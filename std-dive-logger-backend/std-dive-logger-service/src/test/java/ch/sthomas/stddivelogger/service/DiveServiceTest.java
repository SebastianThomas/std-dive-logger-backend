package ch.sthomas.stddivelogger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.data.service.UserDataService;
import ch.sthomas.stddivelogger.data.service.storage.StorageService;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.CcrUnit;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.user.User;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class DiveServiceTest {
    @Test
    void testPagedResponse() {
        final var users =
                List.of(
                        new UserEntity(1, "email@email.ch", "abc123", "name"),
                        new UserEntity(2, "email2@email.ch", "abc", "other"));
        final var pageable = Pageable.ofSize(5);
        final var response =
                PagedResponse.of(new PageImpl<>(users, pageable, 2), UserEntity::toRecord);
        assertEquals(users.stream().map(UserEntity::toRecord).toList(), response.result());
        assertEquals(pageable.getPageSize(), response.pageSize());
    }

    private static final User USER =
            new User(1, "e@e.ch", "pw", "name", true, Instant.now(), Instant.now(), null, null);

    private static DiveService serviceWithLinkedCcrUnit(final Optional<CcrUnit> linked) {
        final var diveDataService = mock(DiveDataService.class);
        when(diveDataService.findCcrUnitLinkedToComputer(anyLong())).thenReturn(linked);
        return new DiveService(
                diveDataService, mock(StorageService.class), mock(UserDataService.class));
    }

    private static DiveProfileUpload profileOnComputer(final long computerId) {
        return new DiveProfileUpload(computerId, Instant.now(), Instant.now(), List.of());
    }

    @Test
    void inferConfigurationFromComputerFillsInBaseAndCcrUnitWhenComputerIsLinked() {
        final var linkedUnit =
                new CcrUnit(5L, 1L, "rEvo", "", false, BaseConfiguration.SIDEMOUNT_CCR);
        final var service = serviceWithLinkedCcrUnit(Optional.of(linkedUnit));

        final var result =
                service.inferConfigurationFromComputer(
                        USER, DiveConfiguration.createEmpty(USER), List.of(profileOnComputer(42)));

        assertEquals(BaseConfiguration.SIDEMOUNT_CCR, result.base());
        assertSame(linkedUnit, result.ccrUnit());
    }

    @Test
    void inferConfigurationFromComputerLeavesConfigurationUntouchedWhenComputerHasNoLink() {
        final var service = serviceWithLinkedCcrUnit(Optional.empty());
        final var original = DiveConfiguration.createEmpty(USER);

        final var result =
                service.inferConfigurationFromComputer(
                        USER, original, List.of(profileOnComputer(42)));

        assertEquals(BaseConfiguration.OTHER, result.base());
        assertNull(result.ccrUnit());
    }

    @Test
    void inferConfigurationFromComputerLeavesConfigurationUntouchedWhenUnitHasNoDefaultBase() {
        // A CCR unit exists and is linked, but the diver hasn't confirmed a default base
        // configuration for it yet - nothing safe to guess, so leave the configuration as-is.
        final var linkedUnitWithoutDefault = new CcrUnit(5L, 1L, "rEvo", "", false, null);
        final var service = serviceWithLinkedCcrUnit(Optional.of(linkedUnitWithoutDefault));

        final var result =
                service.inferConfigurationFromComputer(
                        USER, DiveConfiguration.createEmpty(USER), List.of(profileOnComputer(42)));

        assertEquals(BaseConfiguration.OTHER, result.base());
        assertNull(result.ccrUnit());
    }

    @Test
    void inferConfigurationFromComputerNeverOverridesAnAlreadyExplicitCcrUnit() {
        final var linkedUnit =
                new CcrUnit(5L, 1L, "rEvo", "", false, BaseConfiguration.SIDEMOUNT_CCR);
        final var explicitUnit =
                new CcrUnit(9L, 1L, "Other unit", "", false, BaseConfiguration.BACKMOUNT_CCR);
        final var service = serviceWithLinkedCcrUnit(Optional.of(linkedUnit));
        final var explicit =
                new DiveConfiguration(
                        DiveConfiguration.createEmpty(USER).suit(),
                        BaseConfiguration.BACKMOUNT_CCR,
                        null,
                        null,
                        List.of(),
                        explicitUnit);

        final var result =
                service.inferConfigurationFromComputer(
                        USER, explicit, List.of(profileOnComputer(42)));

        assertSame(explicitUnit, result.ccrUnit());
        assertEquals(BaseConfiguration.BACKMOUNT_CCR, result.base());
    }

    @Test
    void inferConfigurationFromComputerLeavesConfigurationUntouchedWithNoProfiles() {
        final var linkedUnit =
                new CcrUnit(5L, 1L, "rEvo", "", false, BaseConfiguration.SIDEMOUNT_CCR);
        final var service = serviceWithLinkedCcrUnit(Optional.of(linkedUnit));

        final var result =
                service.inferConfigurationFromComputer(
                        USER, DiveConfiguration.createEmpty(USER), List.of());

        assertEquals(BaseConfiguration.OTHER, result.base());
        assertNull(result.ccrUnit());
    }
}
