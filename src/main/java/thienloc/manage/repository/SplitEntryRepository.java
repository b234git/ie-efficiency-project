package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import thienloc.manage.entity.SplitEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SplitEntryRepository extends JpaRepository<SplitEntry, Long> {

    @Query("SELECT DISTINCT s FROM SplitEntry s LEFT JOIN FETCH s.details " +
           "WHERE s.productionDate = :date AND s.section = :section AND s.line = :line")
    Optional<SplitEntry> findByProductionDateAndSectionAndLine(
            @Param("date") LocalDate productionDate,
            @Param("section") String section,
            @Param("line") String line);

    @Query("SELECT DISTINCT s FROM SplitEntry s LEFT JOIN FETCH s.details " +
           "WHERE s.productionDate = :date ORDER BY s.section ASC, s.line ASC")
    List<SplitEntry> findByProductionDateOrderBySectionAscLineAsc(@Param("date") LocalDate date);

    @Query("SELECT DISTINCT s FROM SplitEntry s LEFT JOIN FETCH s.details " +
           "WHERE s.status = :status AND s.productionDate < :cutoffDate")
    List<SplitEntry> findByStatusAndProductionDateBefore(
            @Param("status") String status,
            @Param("cutoffDate") LocalDate cutoffDate);

    @Query("SELECT s.linkedDailyProductionId FROM SplitEntry s " +
           "WHERE s.productionDate = :date AND s.linkedDailyProductionId IS NOT NULL")
    List<Long> findLinkedProductionIdsByDate(@Param("date") LocalDate date);

    @Query("SELECT DISTINCT s FROM SplitEntry s LEFT JOIN FETCH s.details " +
           "WHERE s.status = 'PARTIAL' AND s.productionDate BETWEEN :from AND :to " +
           "ORDER BY s.productionDate DESC, s.section ASC")
    List<SplitEntry> findPartialByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT DISTINCT s FROM SplitEntry s LEFT JOIN FETCH s.details " +
           "WHERE s.productionDate BETWEEN :from AND :to " +
           "ORDER BY s.productionDate ASC, s.section ASC, s.line ASC")
    List<SplitEntry> findByProductionDateBetweenOrderByDateAscSectionAscLineAsc(
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT s.linkedDailyProductionId FROM SplitEntry s " +
           "WHERE s.productionDate BETWEEN :from AND :to AND s.linkedDailyProductionId IS NOT NULL")
    List<Long> findLinkedProductionIdsByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
