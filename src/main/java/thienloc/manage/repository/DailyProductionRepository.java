package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import thienloc.manage.entity.DailyProduction;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface DailyProductionRepository extends JpaRepository<DailyProduction, Long> {
        List<DailyProduction> findByProductionDateOrderBySectionAscLineAsc(LocalDate date);

        List<DailyProduction> findByProductionDateBetweenOrderByProductionDateAscSectionAscLineAsc(
                        LocalDate from, LocalDate to);

        List<DailyProduction> findByCreatedBy_UsernameAndProductionDateBetweenOrderByProductionDateDescSectionAsc(
                        String username, LocalDate from, LocalDate to);

        Page<DailyProduction> findByCreatedBy_UsernameOrderByIdDesc(String username, Pageable pageable);

        // All time queries
        List<DailyProduction> findAllByOrderByProductionDateDescSectionAscLineAsc();

        List<DailyProduction> findByCreatedBy_UsernameOrderByProductionDateDescSectionAsc(String username);
}
