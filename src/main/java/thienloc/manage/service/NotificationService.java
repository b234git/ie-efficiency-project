package thienloc.manage.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import thienloc.manage.entity.Notification;
import thienloc.manage.repository.NotificationRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    // ─── Cached notification count (avoid DB query on every HTTP request) ──────
    private static final long CACHE_TTL_MS = 30_000; // 30 seconds
    private final ConcurrentHashMap<String, long[]> countCache = new ConcurrentHashMap<>();

    public void createForRole(String role, String title, String message, String type) {
        // Avoid duplicate unread notifications with the same title
        if (notificationRepository.existsByTitleAndRecipientRoleAndIsReadFalse(title, role)) {
            return;
        }
        Notification notification = Notification.builder()
                .recipientRole(role)
                .title(title)
                .message(message)
                .type(type)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        notificationRepository.save(notification);
        countCache.clear();
    }

    public void notifyAdminAndManager(String title, String message, String type) {
        createForRole("ROLE_ADMIN", title, message, type);
        createForRole("ROLE_MANAGER", title, message, type);
    }

    public List<Notification> getUnread(String role) {
        return notificationRepository.findByRecipientRoleAndIsReadFalseOrderByCreatedAtDesc(role);
    }

    public long getUnreadCount(String role) {
        long[] cached = countCache.get(role);
        if (cached != null && System.currentTimeMillis() - cached[1] < CACHE_TTL_MS) {
            return cached[0];
        }
        long count = notificationRepository.countByRecipientRoleAndIsReadFalse(role);
        countCache.put(role, new long[]{ count, System.currentTimeMillis() });
        return count;
    }

    public List<Notification> getAll(String role) {
        return notificationRepository.findByRecipientRoleOrderByCreatedAtDesc(role);
    }

    public void markAsRead(Long id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setIsRead(true);
            notificationRepository.save(n);
        });
        countCache.clear();
    }

    public void dismissAll(String role) {
        notificationRepository.markAllReadByRole(role);
        countCache.clear();
    }
}
