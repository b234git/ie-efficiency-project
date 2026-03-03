package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import thienloc.manage.entity.MasterDb;

import java.util.Optional;

@Repository
public interface MasterDbRepository extends JpaRepository<MasterDb, Long> {
    Optional<MasterDb> findByRef(String ref);

    java.util.List<MasterDb> findByArticleNo(String articleNo);
}
