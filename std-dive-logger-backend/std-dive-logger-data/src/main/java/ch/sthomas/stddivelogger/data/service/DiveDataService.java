package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.repository.*;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.entity.*;
import ch.sthomas.stddivelogger.model.user.User;

import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.locationtech.jts.geom.Coordinate;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class DiveDataService {
    private final DiveRepository diveRepository;

    private final UserRepository userRepository;
    private final DiveSiteRepository diveSiteRepository;
    private final DiveProfileRepository diveProfileRepository;
    private final DiveComputerRepository diveComputerRepository;
    private final DiveMeasurementRepository diveMeasurementRepository;
    private final DiveComputerManufacturerRepository diveComputerManufacturerRepository;
    private final DiveBuddyNameRepository diveBuddyNameRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final EntityManager entityManager;

    public DiveDataService(
            final DiveRepository diveRepository,
            final UserRepository userRepository,
            final DiveSiteRepository diveSiteRepository,
            final DiveProfileRepository diveProfileRepository,
            final DiveComputerRepository diveComputerRepository,
            final DiveMeasurementRepository diveMeasurementRepository,
            final DiveComputerManufacturerRepository diveComputerManufacturerRepository,
            final DiveBuddyNameRepository diveBuddyNameRepository,
            final NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            final EntityManager entityManager) {
        this.diveRepository = diveRepository;
        this.userRepository = userRepository;
        this.diveSiteRepository = diveSiteRepository;
        this.diveProfileRepository = diveProfileRepository;
        this.diveComputerRepository = diveComputerRepository;
        this.diveMeasurementRepository = diveMeasurementRepository;
        this.diveComputerManufacturerRepository = diveComputerManufacturerRepository;
        this.diveBuddyNameRepository = diveBuddyNameRepository;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.entityManager = entityManager;
    }

    public List<Dive> findDivesByUser(final User user) {
        return diveRepository.findByUser_Id(user.id()).stream().map(DiveEntity::toRecord).toList();
    }

    public Optional<Dive> findDiveById(final long id) {
        return diveRepository.findById(id).map(DiveEntity::toRecord);
    }

    public Dive saveDive(
            final User user,
            final int number,
            final String diveIdentifier,
            final long diveSiteId,
            final List<DiveProfileUpload> profiles,
            final List<String> namedBuddies)
            throws NoSuchElementException {
        final var userEntity = userRepository.findById(user.id()).orElseThrow();
        final var diveSite = diveSiteRepository.findById(diveSiteId).orElseThrow();

        final var profileEntities = profiles.stream().map(this::createDiveProfileEntity).toList();
        final var entity =
                new DiveEntity(
                        number,
                        diveIdentifier,
                        userEntity,
                        diveSite,
                        profileEntities,
                        namedBuddies);
        return diveRepository.save(entity).toRecord();
    }

    public void saveBuddies(final long diveId, final List<String> buddies) {
        entityManager.flush();

        namedParameterJdbcTemplate.batchUpdate(
                "INSERT INTO t_dive_buddy_name (fk_dive_id, name) VALUES (:diveId, :buddyName)",
                buddies.stream()
                        .map(
                                buddy ->
                                        new MapSqlParameterSource()
                                                .addValue("diveId", diveId)
                                                .addValue("buddyName", buddy))
                        .toArray(MapSqlParameterSource[]::new));

        // TODO: Add clear here if dive is not reloaded with all buddies
        // entityManager.clear();
    }

    private DiveProfileEntity createDiveProfileEntity(final DiveProfileUpload diveProfileUpload) {
        final var computer =
                diveComputerRepository.findById(diveProfileUpload.diveComputerId()).orElseThrow();
        return new DiveProfileEntity(
                computer,
                diveProfileUpload.start(),
                diveProfileUpload.end(),
                diveProfileUpload.measurements().stream().map(DiveMeasurementEntity::new).toList());
    }

    public Optional<DiveSite> findDiveSiteByName(final String diveSite) {
        return diveSiteRepository.findByNameIgnoreCase(diveSite).map(DiveSiteEntity::toRecord);
    }

    public List<DiveSite> findDiveSiteByNameContains(final String partialName) {
        return diveSiteRepository
                .findByNameContainingOrderedByClosestMatch(partialName, Pageable.ofSize(10))
                .stream()
                .map(DiveSiteEntity::toRecord)
                .toList();
    }

    public Optional<DiveComputer> findDiveComputerByUserAndName(
            final long userId, final String customName) {
        return diveComputerRepository
                .findByCustomIdentifierAndUser_Id(customName, userId)
                .map(DiveComputerEntity::toRecord);
    }

    public DiveComputer saveDiveComputer(
            @Nullable final String serialNumber,
            @NotNull final String customIdentifier,
            final String manufacturer,
            final long userId) {
        final var userEntity = userRepository.findById(userId).orElseThrow();
        final var manufacturerEntity =
                diveComputerManufacturerRepository
                        .findByNameIgnoreCase(manufacturer)
                        .orElseGet(() -> new DiveComputerManufacturerEntity(manufacturer));
        return diveComputerRepository
                .save(
                        new DiveComputerEntity(
                                serialNumber, customIdentifier, manufacturerEntity, userEntity))
                .toRecord();
    }

    public long getDiveCount() {
        return diveRepository.count();
    }

    public Optional<User> findUserForDive(final long diveId) {
        return diveRepository
                .findById(diveId)
                .map(DiveEntity::getUserEntity)
                .map(UserEntity::toRecord);
    }

    public Dive updateDive(@NotNull @Valid final Dive dive) {
        final var existingDive = diveRepository.findById(dive.id()).orElseThrow();
        final var diveSiteEntity =
                Optional.ofNullable(dive.site())
                        .map(DiveSite::id)
                        .flatMap(diveSiteRepository::findById)
                        .orElse(null);
        return diveRepository
                .save(existingDive.update(dive.number(), dive.customIdentifier(), diveSiteEntity))
                .toRecord();
    }

    public Dive addProfilesToDive(final long baseDiveId, final long toAddDiveId) {
        final var baseDiveEntity = diveRepository.findById(baseDiveId).orElseThrow();
        final var toAddDiveEntity = diveRepository.findById(toAddDiveId).orElseThrow();
        baseDiveEntity.addProfiles(toAddDiveEntity.getProfiles());
        return diveRepository.save(baseDiveEntity).toRecord();
    }

    public void deleteDiveById(final long toAddDiveId) {
        diveRepository.deleteById(toAddDiveId);
    }

    public List<Dive> findDivesByProfileIds(final List<Long> profileIds) {
        return diveRepository.findByProfileIds(profileIds).stream()
                .map(DiveEntity::toRecord)
                .toList();
    }

    public Dive moveProfiles(final Long targetDiveId, final List<Long> profileIds) {
        diveRepository.setDiveIdWhereProfileIdIn(targetDiveId, profileIds);
        return diveRepository.findById(targetDiveId).map(DiveEntity::toRecord).orElseThrow();
    }

    public Optional<DiveSite> findDiveSiteById(final long id) {
        return diveSiteRepository.findById(id).map(DiveSiteEntity::toRecord);
    }

    public List<DiveSite> findDiveSitesByLocation(final Coordinate coordinate) {
        return findDiveSiteByLocationDistanceWithin(coordinate, 0.005);
    }

    public List<DiveSite> findDiveSiteByLocationDistanceWithin(
            final Coordinate coordinate, final double dist) {
        return diveSiteRepository.findByLocationNear(coordinate, dist).stream()
                .map(DiveSiteEntity::toRecord)
                .toList();
    }

    public DiveSite saveDiveSite(final String name, final Coordinate coordinate) {
        return diveSiteRepository.save(new DiveSiteEntity(name, coordinate)).toRecord();
    }
}
