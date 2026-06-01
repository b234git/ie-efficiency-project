package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import thienloc.manage.entity.UserFeatureOverride;

import java.util.List;

public interface UserFeatureOverrideRepository extends JpaRepository<UserFeatureOverride, Long> {
    List<UserFeatureOverride> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
