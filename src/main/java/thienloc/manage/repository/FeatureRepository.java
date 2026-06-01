package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import thienloc.manage.entity.Feature;

import java.util.Optional;

public interface FeatureRepository extends JpaRepository<Feature, Long> {
    Optional<Feature> findByFeatureKey(String featureKey);
}
