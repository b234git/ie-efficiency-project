package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import thienloc.manage.entity.NewStyleEntry;

import java.util.List;

public interface NewStyleEntryRepository extends JpaRepository<NewStyleEntry, Long> {

    List<NewStyleEntry> findAllByOrderBySectionAscLineAscStyleAsc();

    List<NewStyleEntry> findByDataMonthOrderBySectionAscLineAscStyleAsc(String dataMonth);

    List<NewStyleEntry> findBySectionAndLine(String section, String line);

    List<NewStyleEntry> findByDataMonthAndSectionAndLine(String dataMonth, String section, String line);
}
