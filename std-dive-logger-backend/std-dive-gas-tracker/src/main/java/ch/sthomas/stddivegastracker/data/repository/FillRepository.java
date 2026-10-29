package ch.sthomas.stddivegastracker.data.repository;

import ch.sthomas.stddivegastracker.calc.allocation.TransferGraph;
import ch.sthomas.stddivegastracker.model.entity.FillEntity;
import ch.sthomas.stddivegastracker.model.team.TeamFacts.StageRow;

import jakarta.persistence.LockModeType;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FillRepository extends JpaRepository<FillEntity, Long> {

    /**
     * Stages of the team's non-voided fills that move gas between vessels: drawn from one, or
     * filled into one (DOMAIN §6, §7). AIR into a cylinder is neither, so it is not loaded.
     */
    @Query(
            """
            select new ch.sthomas.stddivegastracker.model.team.TeamFacts$StageRow(
                s.id, f.id, f.filledAt, f.targetVesselId, f.chargedMemberId, s.sourceVesselId,
                s.sourceO2, s.sourceHe, s.addedLitres, s.addedO2Litres, s.addedHeLitres,
                s.lineLossLitres, s.drawLitres)
            from FillEntity f join f.stages s
            where f.teamId = :teamId and f.voidedAt is null
              and (f.targetVesselId is not null or s.sourceVesselId is not null)
            order by f.filledAt, f.id, s.stageIndex
            """)
    List<StageRow> findGasStageRows(Long teamId);

    Optional<FillEntity> findByIdAndTeamId(Long id, Long teamId);

    /** Non-voided cylinder fills per charged member in {@code [from, to)} (API.md §6). */
    @Query(
            """
            select new ch.sthomas.stddivegastracker.data.repository.FillRepository$MemberFillCount(
                f.chargedMemberId, count(f))
            from FillEntity f
            where f.teamId = :teamId and f.voidedAt is null and f.chargedMemberId is not null
              and f.filledAt >= :from and f.filledAt < :to
            group by f.chargedMemberId
            """)
    List<MemberFillCount> countCylinderFills(Long teamId, Instant from, Instant to);

    /** Whether gas was blended into the vessel (an active fill targets it). */
    boolean existsByTeamIdAndTargetVesselIdAndVoidedAtIsNull(Long teamId, Long targetVesselId);

    /** One row of {@link #countCylinderFills}. */
    record MemberFillCount(Long memberId, Long fills) {}

    /** The latest active fill into or out of a bank: the bank can only close after it. */
    @Query(
            """
            select max(f.filledAt) from FillEntity f
            where f.teamId = :teamId and f.voidedAt is null
              and (f.targetVesselId = :vesselId
                   or exists (select 1 from f.stages s where s.sourceVesselId = :vesselId))
            """)
    Optional<Instant> findLatestFillOf(Long teamId, Long vesselId);

    /** Serialises edit and void of one fill; hold it for the transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FillEntity f where f.id = :id and f.teamId = :teamId")
    Optional<FillEntity> findForUpdate(Long id, Long teamId);

    /**
     * The fills list, newest first (API.md §4). {@code memberId}: the charged member; {@code
     * vesselId}: the target bank or any stage's source bank.
     */
    @Query(
            """
            select f from FillEntity f
            where f.teamId = :teamId
              and (:includeVoided = true or f.voidedAt is null)
              and (:memberId is null or f.chargedMemberId = :memberId)
              and (:vesselId is null or f.targetVesselId = :vesselId
                   or exists (select 1 from f.stages s where s.sourceVesselId = :vesselId))
            order by f.filledAt desc, f.id desc
            """)
    Page<FillEntity> findPage(
            Long teamId,
            boolean includeVoided,
            @Nullable Long memberId,
            @Nullable Long vesselId,
            Pageable pageable);

    /** One query for the stages of a whole page (instead of one per fill). */
    @Query("select distinct f from FillEntity f left join fetch f.stages where f.id in :ids")
    List<FillEntity> findWithStages(List<Long> ids);

    /**
     * The team's transfer graph (DOMAIN §5.6): source bank -> target bank of its non-voided fills,
     * without {@code excludedFillId} (the fill being replaced; 0 = none).
     */
    @Query(
            """
            select distinct new ch.sthomas.stddivegastracker.calc.allocation.TransferGraph$Edge(
                s.sourceVesselId, f.targetVesselId)
            from FillEntity f join f.stages s
            where f.teamId = :teamId and f.voidedAt is null and f.id <> :excludedFillId
              and f.targetVesselId is not null and s.sourceVesselId is not null
            """)
    List<TransferGraph.Edge> findTransferEdges(Long teamId, long excludedFillId);
}
