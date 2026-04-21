package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import thienloc.manage.entity.ImportJob;

import java.util.List;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
    List<ImportJob> findTop10ByCreatedByOrderByCreatedAtDesc(String createdBy);
}
