package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import thienloc.manage.entity.DailyProductionDetail;

@Repository
public interface DailyProductionDetailRepository extends JpaRepository<DailyProductionDetail, Long> {
}
