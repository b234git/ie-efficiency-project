package thienloc.manage.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import thienloc.manage.entity.MasterDb;

import java.util.Optional;

@Repository
public interface MasterDbRepository extends JpaRepository<MasterDb, Long> {
    Optional<MasterDb> findByRef(String ref);

    java.util.List<MasterDb> findByArticleNo(String articleNo);

    Page<MasterDb> findByRefContainingIgnoreCaseOrArticleNoContainingIgnoreCase(
            String ref, String articleNo, Pageable pageable);
}
