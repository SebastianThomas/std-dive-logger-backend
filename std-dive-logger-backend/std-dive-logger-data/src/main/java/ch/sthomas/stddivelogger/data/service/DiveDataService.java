package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.repository.*;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.entity.*;
import ch.sthomas.stddivelogger.model.user.User;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class DiveDataService {
    final DiveRepository diveRepository;
    private final UserRepository userRepository;
    private final DiveSiteRepository diveSiteRepository;
    private final DiveProfileRepository diveProfileRepository;
    private final DiveComputerRepository diveComputerRepository;
    private final DiveMeasurementRepository diveMeasurementRepository;
    private final DiveComputerManufacturerRepository diveComputerManufacturerRepository;

    public DiveDataService(
            final DiveRepository diveRepository,
            final UserRepository userRepository,
            final DiveSiteRepository diveSiteRepository,
            final DiveProfileRepository diveProfileRepository,
            final DiveComputerRepository diveComputerRepository,
            final DiveMeasurementRepository diveMeasurementRepository,
            DiveComputerManufacturerRepository diveComputerManufacturerRepository) {
        this.diveRepository = diveRepository;
        this.userRepository = userRepository;
        this.diveSiteRepository = diveSiteRepository;
        this.diveProfileRepository = diveProfileRepository;
        this.diveComputerRepository = diveComputerRepository;
        this.diveMeasurementRepository = diveMeasurementRepository;
        this.diveComputerManufacturerRepository = diveComputerManufacturerRepository;
    }

    public List<Dive> findDivesByUser(final User user) {
        return diveRepository.findByUser_Id(user.id()).stream().map(DiveEntity::toRecord).toList();
    }

    public Dive saveDive(
            final int number,
            final String diveIdentifier,
            final long userId,
            final long diveSiteId,
            final List<DiveProfileUpload> profiles)
            throws NoSuchElementException {
        final var user = userRepository.findById(userId).orElseThrow();
        final var diveSite = diveSiteRepository.findById(diveSiteId).orElseThrow();

        final var profileEntities = profiles.stream().map(this::createDiveProfileEntity).toList();
        final var entity = new DiveEntity(number, diveIdentifier, user, diveSite, profileEntities);
        return diveRepository.save(entity).toRecord();
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
                .findByNameContainingClosestMatch(partialName, Pageable.ofSize(10))
                .stream()
                .map(DiveSiteEntity::toRecord)
                // .sorted(
                //        Comparator.comparing(
                //                d ->
                //                        (d.name().indexOf(partialName) + 1)
                //                                * (d.name().length() - partialName.length())))
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
}
