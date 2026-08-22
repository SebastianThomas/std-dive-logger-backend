package ch.sthomas.stddivelogger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.data.service.UserDataService;
import ch.sthomas.stddivelogger.data.service.storage.ObjectStorageService;
import ch.sthomas.stddivelogger.model.controller.dive.DiveSiteWithDives;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.BasicDiveInfo;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
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
                diveDataService, mock(ObjectStorageService.class), mock(UserDataService.class));
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
    void inferConfigurationFromComputerIgnoresAUnitBelongingToAnotherUser() {
        // Defense in depth against the computer<->CCR-unit link somehow pointing at a unit that
        // isn't the caller's own - see the IDOR this was added alongside (updateDiveComputer
        // previously let anyone re-link *any* user's computer to a unit they own themselves).
        final var otherUsersUnit =
                new CcrUnit(
                        5L, 2L, "Someone else's rEvo", "", false, BaseConfiguration.SIDEMOUNT_CCR);
        final var service = serviceWithLinkedCcrUnit(Optional.of(otherUsersUnit));

        final var result =
                service.inferConfigurationFromComputer(
                        USER, DiveConfiguration.createEmpty(USER), List.of(profileOnComputer(42)));

        assertEquals(BaseConfiguration.OTHER, result.base());
        assertNull(result.ccrUnit());
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

    private static DiveSiteWithDives<DiveSite> siteWithDives(final long id, final int diveCount) {
        final var site = new DiveSite(id, "Site " + id, 0.0, 0.0);
        final var diveInfo =
                java.util.stream.IntStream.range(0, diveCount)
                        .mapToObj(i -> new BasicDiveInfo(i, i, "Dive " + i))
                        .toList();
        return new DiveSiteWithDives<>(site, diveInfo.size(), diveInfo);
    }

    private static DiveService serviceWithSites(final List<DiveSiteWithDives<DiveSite>> sites) {
        final var diveDataService = mock(DiveDataService.class);
        when(diveDataService.findDiveSitesByUser(
                        anyLong(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(sites);
        return new DiveService(
                diveDataService, mock(ObjectStorageService.class), mock(UserDataService.class));
    }

    @Test
    void getSitesByUserKeepsFullDiveInfoAtOrBelowTheLightweightThreshold() {
        final var sites =
                java.util.stream.LongStream.range(0, DiveService.SITE_LIST_LIGHTWEIGHT_THRESHOLD)
                        .mapToObj(id -> siteWithDives(id, 2))
                        .toList();
        final var service = serviceWithSites(sites);

        final var result = service.getSitesByUser(USER, true);

        assertEquals(DiveService.SITE_LIST_LIGHTWEIGHT_THRESHOLD, result.size());
        assertNotNull(result.getFirst().diveInfo());
        assertEquals(2, result.getFirst().diveInfo().size());
        assertEquals(2, result.getFirst().diveCount());
    }

    @Test
    void getSitesByUserStripsDiveInfoButKeepsCountAboveTheLightweightThreshold() {
        final var sites =
                java.util.stream.LongStream.range(
                                0, DiveService.SITE_LIST_LIGHTWEIGHT_THRESHOLD + 1)
                        .mapToObj(id -> siteWithDives(id, 3))
                        .toList();
        final var service = serviceWithSites(sites);

        final var result = service.getSitesByUser(USER, true);

        assertEquals(DiveService.SITE_LIST_LIGHTWEIGHT_THRESHOLD + 1, result.size());
        assertNull(result.getFirst().diveInfo());
        // The site itself (with its coordinates) and the count are always present, even stripped.
        assertNotNull(result.getFirst().site());
        assertEquals(3, result.getFirst().diveCount());
    }

    @Test
    void searchDivesWithIncludeReaderTrueDoesNotThrowAndDelegatesToTheSameSearch() {
        final var diveDataService = mock(DiveDataService.class);
        final var expected =
                new PagedResponse<ch.sthomas.stddivelogger.model.dive.SimplifiedDive>(
                        0, 0, 0, List.of());
        when(diveDataService.searchDives(
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq("wreck"),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(expected);
        final var service =
                new DiveService(
                        diveDataService,
                        mock(ObjectStorageService.class),
                        mock(UserDataService.class));

        final var result = service.searchDives(USER, "wreck", true, 0);

        assertSame(expected, result);
    }
}
