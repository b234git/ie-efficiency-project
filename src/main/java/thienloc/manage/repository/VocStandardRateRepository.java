package thienloc.manage.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import thienloc.manage.entity.VocStandardRate;

import java.util.Optional;

@Repository
public interface VocStandardRateRepository extends JpaRepository<VocStandardRate, Long> {

    Optional<VocStandardRate> findByArticleNoAndChemicalCode(String articleNo, String chemicalCode);

    // Paged search by article or chemical (LIMIT/OFFSET at the DB — never loads the whole table)
    Page<VocStandardRate> findByArticleNoContainingIgnoreCaseOrChemicalCodeContainingIgnoreCase(
            String articleNo, String chemicalCode, Pageable pageable);
}
