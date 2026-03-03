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

    Page<DailyProduction> findByCreatedBy_UsernameOrderByProductionDateDesc(String username, Pageable pageable);
}
