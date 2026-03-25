package thienloc.manage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import thienloc.manage.entity.Notification;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientRoleAndIsReadFalseOrderByCreatedAtDesc(String recipientRole);

    long countByRecipientRoleAndIsReadFalse(String recipientRole);

    List<Notification> findByRecipientRoleOrderByCreatedAtDesc(String recipientRole);

    boolean existsByTitleAndRecipientRoleAndIsReadFalse(String title, String recipientRole);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipientRole = :role AND n.isRead = false")
    void markAllReadByRole(@Param("role") String role);
}
