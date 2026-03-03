package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import thienloc.manage.entity.SystemLog;

import java.util.List;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {
    List<SystemLog> findAllByOrderByTimestampDesc();
}
