package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import thienloc.manage.entity.VocConsumption;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface VocConsumptionRepository extends JpaRepository<VocConsumption, Long> {

    List<VocConsumption> findByProductionDateBetweenOrderByProductionDateAscLineAscChemicalCodeAsc(
            LocalDate from, LocalDate to);

    List<VocConsumption> findByProductionDateAndSectionAndLineOrderByChemicalCodeAsc(
            LocalDate productionDate, String section, String line);

    /** Whole-day log across every line (the "Actual" sheet view when no line is picked). */
    List<VocConsumption> findByProductionDateAndSectionOrderByLineAscChemicalCodeAsc(
            LocalDate productionDate, String section);

    Optional<VocConsumption> findByProductionDateAndSectionAndLineAndChemicalCode(
            LocalDate productionDate, String section, String line, String chemicalCode);

    @Query("SELECT DISTINCT TO_CHAR(c.productionDate, 'YYYY-MM') FROM VocConsumption c ORDER BY 1 DESC")
    List<String> findDistinctMonths();

    // ─── Retention ───────────────────────────────────────────────────────────
    long countByProductionDateBefore(LocalDate cutoffDate);

    @Modifying
    @Transactional
    @Query("DELETE FROM VocConsumption c WHERE c.productionDate < :cutoffDate")
    int deleteByProductionDateBefore(@Param("cutoffDate") LocalDate cutoffDate);
}
