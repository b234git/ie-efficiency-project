package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import thienloc.manage.entity.EffIncentiveRate;

import java.util.List;

@Repository
public interface EffIncentiveRateRepository extends JpaRepository<EffIncentiveRate, Long> {
    List<EffIncentiveRate> findBySecOrderByEffPercentAsc(String sec);
    List<EffIncentiveRate> findAllByOrderBySecAscEffPercentAsc();

    @Query("SELECT DISTINCT r.sec FROM EffIncentiveRate r ORDER BY r.sec ASC")
    List<String> findDistinctSecs();
}
