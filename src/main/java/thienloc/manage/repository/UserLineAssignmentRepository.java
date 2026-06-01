package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import thienloc.manage.entity.UserLineAssignment;

import java.util.List;

@Repository
public interface UserLineAssignmentRepository extends JpaRepository<UserLineAssignment, Long> {

    List<UserLineAssignment> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
