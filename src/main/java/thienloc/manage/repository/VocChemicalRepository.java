package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import thienloc.manage.entity.VocChemical;

import java.util.List;
import java.util.Optional;

@Repository
public interface VocChemicalRepository extends JpaRepository<VocChemical, Long> {

    List<VocChemical> findAllByOrderByCodeAsc();

    List<VocChemical> findByActiveTrueOrderByCodeAsc();

    Optional<VocChemical> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
