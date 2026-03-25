package thienloc.manage.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import thienloc.manage.entity.MasterDb;

import java.util.List;
import java.util.Optional;

@Repository
public interface MasterDbRepository extends JpaRepository<MasterDb, Long> {

    // ─── Legacy (backward compat, used as fallback) ──────────────────────────────
    Optional<MasterDb> findByRef(String ref);
    List<MasterDb> findByArticleNo(String articleNo);
    Optional<MasterDb> findFirstByArticleNoOrderByRefAsc(String articleNo);
    List<MasterDb> findByArticleNoInOrderByRefAsc(java.util.Collection<String> articleNos);
    List<MasterDb> findByArticleNoInAndDataMonthOrderByRefAsc(java.util.Collection<String> articleNos, String dataMonth);
    Page<MasterDb> findByRefContainingIgnoreCaseOrArticleNoContainingIgnoreCase(
            String ref, String articleNo, Pageable pageable);

    // ─── Month-aware queries ─────────────────────────────────────────────────────
    Optional<MasterDb> findByRefAndDataMonth(String ref, String dataMonth);
    Optional<MasterDb> findFirstByArticleNoAndDataMonthOrderByRefAsc(String articleNo, String dataMonth);
    List<MasterDb> findByDataMonth(String dataMonth);
    Page<MasterDb> findByDataMonth(String dataMonth, Pageable pageable);

    @Query("SELECT DISTINCT m.dataMonth FROM MasterDb m WHERE m.dataMonth IS NOT NULL ORDER BY m.dataMonth DESC")
    List<String> findDistinctDataMonths();

    // Month-aware search (keyword + month filter)
    @Query("SELECT m FROM MasterDb m WHERE m.dataMonth = :dm AND " +
           "(LOWER(m.ref) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(m.articleNo) LIKE LOWER(CONCAT('%', :kw, '%')))")
    Page<MasterDb> searchByKeywordAndMonth(@Param("kw") String keyword, @Param("dm") String dataMonth, Pageable pageable);

    // ─── Migration ───────────────────────────────────────────────────────────────
    @Modifying
    @Transactional
    @Query("UPDATE MasterDb m SET m.dataMonth = :month WHERE m.dataMonth IS NULL")
    int updateNullDataMonth(@Param("month") String month);

    // ─── Retention ───────────────────────────────────────────────────────────────
    @Query("SELECT m FROM MasterDb m WHERE m.dataMonth < :cutoffMonth")
    List<MasterDb> findByDataMonthBefore(@Param("cutoffMonth") String cutoffMonth);

    @Query("SELECT COUNT(m) FROM MasterDb m WHERE m.dataMonth < :cutoffMonth")
    long countByDataMonthBefore(@Param("cutoffMonth") String cutoffMonth);

    @Modifying
    @Transactional
    @Query("DELETE FROM MasterDb m WHERE m.dataMonth < :cutoffMonth")
    int deleteByDataMonthBefore(@Param("cutoffMonth") String cutoffMonth);

    long countByDataMonthIsNull();
}
