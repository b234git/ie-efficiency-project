package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import thienloc.manage.entity.VocProduction;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface VocProductionRepository extends JpaRepository<VocProduction, Long> {

    List<VocProduction> findByProductionDateBetween(LocalDate from, LocalDate to);

    Optional<VocProduction> findByProductionDateAndSectionAndLineAndArticleNo(
            LocalDate productionDate, String section, String line, String articleNo);
}
