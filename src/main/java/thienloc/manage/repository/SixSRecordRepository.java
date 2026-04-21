package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import thienloc.manage.entity.SixSRecord;

import java.util.List;
import java.util.Optional;

public interface SixSRecordRepository extends JpaRepository<SixSRecord, Long> {

    List<SixSRecord> findByDataMonthOrderBySectionAscLineAsc(String dataMonth);

    Optional<SixSRecord> findByDataMonthAndSectionAndLine(String dataMonth, String section, String line);

    @Query("SELECT DISTINCT s.dataMonth FROM SixSRecord s ORDER BY s.dataMonth DESC")
    List<String> findDistinctDataMonths();
}
