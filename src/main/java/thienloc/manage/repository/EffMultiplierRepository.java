package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import thienloc.manage.entity.EffMultiplier;

import java.util.List;
import java.util.Optional;

@Repository
public interface EffMultiplierRepository extends JpaRepository<EffMultiplier, Long> {
    Optional<EffMultiplier> findBySec(String sec);
    List<EffMultiplier> findAllByOrderBySecAsc();
}
