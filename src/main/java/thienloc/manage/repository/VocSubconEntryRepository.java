package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import thienloc.manage.entity.VocSubconEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface VocSubconEntryRepository extends JpaRepository<VocSubconEntry, Long> {

    /** Report load: entries + their chemical details in one fetch (anti-N+1). */
    @Query("SELECT DISTINCT e FROM VocSubconEntry e LEFT JOIN FETCH e.details " +
           "WHERE e.productionDate BETWEEN :from AND :to " +
           "ORDER BY e.productionDate, e.subcontractor, e.articleNo")
    List<VocSubconEntry> findWithDetailsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    Optional<VocSubconEntry> findByProductionDateAndSubcontractorAndArticleNo(
            LocalDate productionDate, String subcontractor, String articleNo);

    @Query("SELECT DISTINCT TO_CHAR(e.productionDate, 'YYYY-MM') FROM VocSubconEntry e ORDER BY 1 DESC")
    List<String> findDistinctMonths();

    @Query("SELECT DISTINCT e.subcontractor FROM VocSubconEntry e ORDER BY 1")
    List<String> findDistinctSubcontractors();
}
