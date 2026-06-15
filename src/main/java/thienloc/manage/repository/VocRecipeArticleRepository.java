package thienloc.manage.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import thienloc.manage.entity.VocRecipeArticle;

@Repository
public interface VocRecipeArticleRepository extends JpaRepository<VocRecipeArticle, String> {

    // Paged search by article, model code, or model name (LIMIT/OFFSET at the DB).
    Page<VocRecipeArticle>
    findByArticleNoContainingIgnoreCaseOrModelCodeContainingIgnoreCaseOrModelNameContainingIgnoreCase(
            String articleNo, String modelCode, String modelName, Pageable pageable);
}
