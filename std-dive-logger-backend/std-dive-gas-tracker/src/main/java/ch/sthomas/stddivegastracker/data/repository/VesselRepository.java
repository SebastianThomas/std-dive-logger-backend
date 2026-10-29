package ch.sthomas.stddivegastracker.data.repository;

import ch.sthomas.stddivegastracker.model.entity.VesselEntity;
import ch.sthomas.stddivegastracker.model.team.TeamFacts.CostLineRow;
import ch.sthomas.stddivegastracker.model.team.TeamFacts.ParticipantRow;
import ch.sthomas.stddivegastracker.model.team.TeamFacts.ReadingRow;
import ch.sthomas.stddivegastracker.model.team.TeamFacts.VesselRow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * The row queries are the flat reads behind {@code TeamFacts}: one query per table for the whole
 * team, never per vessel (no entity-graph hydration).
 */
@Repository
public interface VesselRepository extends JpaRepository<VesselEntity, Long> {

    Optional<VesselEntity> findByIdAndTeamId(Long id, Long teamId);

    @Query(
            """
            select new ch.sthomas.stddivegastracker.model.team.TeamFacts$VesselRow(
                v.id, v.label, v.waterVolumeLitres, v.mixO2, v.mixHe, v.initialO2, v.initialHe,
                v.initialPressureBar,
                v.expectedResidualPressureBar, v.lossPolicy, v.openedAt, v.closedAt,
                v.closingPressureBar, v.notes, v.createdByMemberId)
            from VesselEntity v where v.teamId = :teamId order by v.id
            """)
    List<VesselRow> findRows(Long teamId);

    @Query(
            """
            select new ch.sthomas.stddivegastracker.model.team.TeamFacts$CostLineRow(
                v.id, c.id, c.payerMemberId, c.amount, c.paidAt, c.note)
            from VesselEntity v join v.costLines c
            where v.teamId = :teamId order by c.paidAt, c.id
            """)
    List<CostLineRow> findCostLineRows(Long teamId);

    @Query(
            """
            select new ch.sthomas.stddivegastracker.model.team.TeamFacts$ReadingRow(
                v.id, r.id, r.pressureBar, r.measuredAt, r.createdByMemberId)
            from VesselEntity v join v.readings r
            where v.teamId = :teamId order by r.measuredAt, r.id
            """)
    List<ReadingRow> findReadingRows(Long teamId);

    @Query(
            """
            select new ch.sthomas.stddivegastracker.model.team.TeamFacts$ParticipantRow(v.id, p)
            from VesselEntity v join v.lossParticipantMemberIds p
            where v.teamId = :teamId order by v.id, p
            """)
    List<ParticipantRow> findParticipantRows(Long teamId);

    /**
     * The readings a fill produced (edit and void drop them, DOMAIN §6). A bulk delete: load the
     * vessels' reading lists only after it.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from VesselReadingEntity r where r.fillId = :fillId")
    void deleteReadingsOfFill(Long fillId);
}
