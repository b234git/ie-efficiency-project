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

        // JOIN FETCH to load details in a single query, avoids N+1
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

        // ─── Distinct months ───────────────────────────────────────────────────────
        @Query("SELECT DISTINCT TO_CHAR(p.productionDate, 'YYYY-MM') FROM DailyProduction p ORDER BY 1 DESC")
        List<String> findDistinctMonths();

        // ─── DB-level pagination (two-pass) ───────────────────────────────────────
        // Pass 1: lấy IDs có LIMIT/OFFSET — section/line lọc tại DB, KHÔNG dùng JOIN FETCH
        // (JOIN FETCH + Pageable sẽ gây HibernateJpaDialect warning và paginate in-memory)
        @Query("SELECT p.id FROM DailyProduction p " +
               "WHERE p.createdBy.username = :username " +
               "AND p.productionDate BETWEEN :from AND :to " +
               "AND (:section = '' OR p.section = :section) " +
               "AND (:line = '' OR LOWER(p.line) LIKE LOWER(CONCAT('%', :line, '%'))) " +
               "ORDER BY p.productionDate DESC, p.section ASC, p.line ASC")
        Page<Long> findIdsByUsernameAndDateRange(
                @Param("username") String username,
                @Param("from") LocalDate from,
                @Param("to") LocalDate to,
                @Param("section") String section,
                @Param("line") String line,
                Pageable pageable);

        @Query("SELECT p.id FROM DailyProduction p " +
               "WHERE p.createdBy.username = :username " +
               "AND p.productionDate BETWEEN :from AND :to " +
               "AND (:section = '' OR p.section = :section) " +
               "AND (:line = '' OR LOWER(p.line) LIKE LOWER(CONCAT('%', :line, '%'))) " +
               "ORDER BY p.productionDate DESC, p.section ASC, p.line ASC")
        List<Long> findAllIdsByUsernameAndDateRange(
                @Param("username") String username,
                @Param("from") LocalDate from,
                @Param("to") LocalDate to,
                @Param("section") String section,
                @Param("line") String line);

        // Pass 2: load đúng các entity theo IDs với JOIN FETCH details (no N+1)
        @Query("SELECT DISTINCT p FROM DailyProduction p LEFT JOIN FETCH p.details " +
               "WHERE p.id IN :ids " +
               "ORDER BY p.productionDate DESC, p.section ASC, p.line ASC")
        List<DailyProduction> findByIdsWithDetails(@Param("ids") List<Long> ids);

        // ─── Retention ─────────────────────────────────────────────────────────────
        List<DailyProduction> findByProductionDateBefore(LocalDate cutoffDate);

        long countByProductionDateBefore(LocalDate cutoffDate);

        @Modifying
        @Transactional
        @Query("DELETE FROM DailyProduction d WHERE d.productionDate < :cutoffDate")
        int deleteByProductionDateBefore(@Param("cutoffDate") LocalDate cutoffDate);
}
