package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import thienloc.manage.entity.ReprocessRecord;

import java.util.List;
import java.util.Optional;

public interface ReprocessRecordRepository extends JpaRepository<ReprocessRecord, Long> {

    List<ReprocessRecord> findByDataMonthOrderBySectionAscLineAsc(String dataMonth);

    Optional<ReprocessRecord> findByDataMonthAndSectionAndLine(String dataMonth, String section, String line);

    @Query("SELECT DISTINCT r.dataMonth FROM ReprocessRecord r ORDER BY r.dataMonth DESC")
    List<String> findDistinctDataMonths();
}
