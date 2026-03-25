package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import thienloc.manage.entity.DailyProduction;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface DailyProductionRepository extends JpaRepository<DailyProduction, Long> {

        // JOIN FETCH để tải details cùng 1 query, tránh N+1
        @Query("SELECT DISTINCT p FROM DailyProduction p LEFT JOIN FETCH p.details " +
               "WHERE p.productionDate = :date ORDER BY p.section ASC, p.line ASC")
        List<DailyProduction> findByProductionDateOrderBySectionAscLineAsc(@Param("date") LocalDate date);

        @Query("SELECT DISTINCT p FROM DailyProduction p LEFT JOIN FETCH p.details " +
               "WHERE p.productionDate BETWEEN :from AND :to " +
               "ORDER BY p.productionDate ASC, p.section ASC, p.line ASC")
        List<DailyProduction> findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(
                        @Param("from") LocalDate from, @Param("to") LocalDate to);

        @Query("SELECT DISTINCT p FROM DailyProduction p LEFT JOIN FETCH p.details " +
               "WHERE p.createdBy.username = :username " +
               "AND p.productionDate BETWEEN :from AND :to " +
               "ORDER BY p.productionDate DESC, p.section ASC")
        List<DailyProduction> findByCreatedBy_UsernameAndProductionDateBetweenOrderByProductionDateDescSectionAsc(
                        @Param("username") String username, @Param("from") LocalDate from, @Param("to") LocalDate to);

        Page<DailyProduction> findByCreatedBy_UsernameOrderByIdDesc(String username, Pageable pageable);

        // All time queries
        @Query("SELECT DISTINCT p FROM DailyProduction p LEFT JOIN FETCH p.details " +
               "ORDER BY p.productionDate DESC, p.section ASC, p.line ASC")
        List<DailyProduction> findAllByOrderByProductionDateDescSectionAscLineAsc();

        Page<DailyProduction> findAllByOrderByIdDesc(Pageable pageable);

        @Query("SELECT DISTINCT p FROM DailyProduction p LEFT JOIN FETCH p.details " +
               "WHERE p.createdBy.username = :username " +
               "ORDER BY p.productionDate DESC, p.section ASC")
        List<DailyProduction> findByCreatedBy_UsernameOrderByProductionDateDescSectionAsc(
                        @Param("username") String username);

        // ─── Retention ─────────────────────────────────────────────────────────────
        List<DailyProduction> findByProductionDateBefore(LocalDate cutoffDate);

        long countByProductionDateBefore(LocalDate cutoffDate);

        @Modifying
        @Transactional
        @Query("DELETE FROM DailyProduction d WHERE d.productionDate < :cutoffDate")
        int deleteByProductionDateBefore(@Param("cutoffDate") LocalDate cutoffDate);
}
