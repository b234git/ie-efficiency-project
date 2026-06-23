package thienloc.manage.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import thienloc.manage.entity.VocStandardRate;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VocStandardRateRepository extends JpaRepository<VocStandardRate, Long> {

    Optional<VocStandardRate> findByArticleNoAndChemicalCode(String articleNo, String chemicalCode);

    Optional<VocStandardRate> findBySectionAndArticleNoAndChemicalCode(
            String section, String articleNo, String chemicalCode);

    // Paged search by article or chemical (LIMIT/OFFSET at the DB — never loads the whole table)
    Page<VocStandardRate> findByArticleNoContainingIgnoreCaseOrChemicalCodeContainingIgnoreCase(
            String articleNo, String chemicalCode, Pageable pageable);

    // All rates for the articles on the current grid page (one query, no N+1)
    List<VocStandardRate> findByArticleNoIn(Collection<String> articleNos);

    // Chemical codes actually used across the recipe — the matrix columns
    @Query("SELECT DISTINCT r.chemicalCode FROM VocStandardRate r ORDER BY r.chemicalCode")
    List<String> findDistinctChemicalCodes();

    @Query("SELECT DISTINCT r.chemicalCode FROM VocStandardRate r WHERE r.section = :section ORDER BY r.chemicalCode")
    List<String> findDistinctChemicalCodesBySection(String section);

    // Sections that actually have a recipe (SEW/ASSY/SF) — for the recipe-page selector
    @Query("SELECT DISTINCT r.section FROM VocStandardRate r ORDER BY r.section")
    List<String> findDistinctSections();
}
