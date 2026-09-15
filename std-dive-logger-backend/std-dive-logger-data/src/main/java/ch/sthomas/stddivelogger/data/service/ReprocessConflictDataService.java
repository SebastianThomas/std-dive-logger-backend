package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.repository.ImportReprocessConflictRepository;
import ch.sthomas.stddivelogger.model.entity.ImportReprocessConflictEntity;
import ch.sthomas.stddivelogger.model.importfile.ImportedDiveField;
import ch.sthomas.stddivelogger.model.importfile.ReprocessConflict;
import ch.sthomas.stddivelogger.model.importfile.ReprocessConflictStatus;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Re-processing results waiting for the diver - see {@link ImportReprocessConflictEntity}. */
@Service
public class ReprocessConflictDataService {

    private record DiveLabel(int number, String identifier) {}

    private final ImportReprocessConflictRepository conflicts;
    private final NamedParameterJdbcTemplate jdbc;

    public ReprocessConflictDataService(
            final ImportReprocessConflictRepository conflicts,
            final NamedParameterJdbcTemplate jdbc) {
        this.conflicts = conflicts;
        this.jdbc = jdbc;
    }

    /** Saves a new open conflict, superseding the open one for the same profile / dive field. */
    @Transactional
    public ImportReprocessConflictEntity open(final ImportReprocessConflictEntity conflict) {
        final var profileId = conflict.getProfileId();
        final var field = conflict.getField();
        if (profileId != null) {
            supersedeOpenForProfile(conflict.getDiveId(), profileId);
        } else if (field != null) {
            supersedeOpenForField(conflict.getDiveId(), field);
        }
        return conflicts.save(conflict);
    }

    @Transactional
    public void supersedeOpenForProfile(final long diveId, final long profileId) {
        supersede(
                conflicts.findByDiveIdAndProfileIdAndStatus(
                        diveId, profileId, ReprocessConflictStatus.OPEN));
    }

    @Transactional
    public void supersedeOpenForField(final long diveId, final ImportedDiveField field) {
        supersede(
                conflicts.findByDiveIdAndFieldAndStatus(
                        diveId, field, ReprocessConflictStatus.OPEN));
    }

    // Flushed right away: Hibernate inserts before it updates, so the replacing OPEN row would
    // otherwise hit the one-open-conflict index before this one is marked superseded.
    private void supersede(final List<ImportReprocessConflictEntity> open) {
        if (open.isEmpty()) {
            return;
        }
        open.forEach(c -> c.resolve(ReprocessConflictStatus.SUPERSEDED));
        conflicts.saveAllAndFlush(open);
    }

    @Transactional(readOnly = true)
    public List<ReprocessConflict> listOpen(final long userId) {
        final var open =
                conflicts.findByUserIdAndStatusOrderByCreatedAtAscIdAsc(
                        userId, ReprocessConflictStatus.OPEN);
        if (open.isEmpty()) {
            return List.of();
        }
        final var labels = new HashMap<Long, DiveLabel>();
        jdbc.query(
                "SELECT pk_dive_id, dive_number, dive_identifier FROM t_dives"
                        + " WHERE pk_dive_id IN (:ids)",
                Map.of("ids", open.stream().map(ImportReprocessConflictEntity::getDiveId).toList()),
                rs -> {
                    labels.put(
                            rs.getLong("pk_dive_id"),
                            new DiveLabel(
                                    rs.getInt("dive_number"),
                                    Optional.ofNullable(rs.getString("dive_identifier"))
                                            .orElse("")));
                });
        return open.stream()
                .map(
                        c -> {
                            final var label =
                                    labels.getOrDefault(c.getDiveId(), new DiveLabel(0, ""));
                            return c.toRecord(label.number(), label.identifier());
                        })
                .toList();
    }

    @Transactional(readOnly = true)
    public long countOpen(final long userId) {
        return conflicts.countByUserIdAndStatus(userId, ReprocessConflictStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public Optional<ImportReprocessConflictEntity> findOwned(final long id, final long userId) {
        return conflicts.findByIdAndUserId(id, userId);
    }

    @Transactional
    public ImportReprocessConflictEntity resolve(
            final ImportReprocessConflictEntity conflict, final ReprocessConflictStatus status) {
        conflict.resolve(status);
        return conflicts.save(conflict);
    }
}
