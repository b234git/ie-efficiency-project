package thienloc.manage.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import thienloc.manage.entity.Notification;
import thienloc.manage.repository.NotificationRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void testCreateForRole_NewNotification() {
        when(notificationRepository.existsByTitleAndRecipientRoleAndIsReadFalse("Test", "ROLE_ADMIN"))
                .thenReturn(false);

        notificationService.createForRole("ROLE_ADMIN", "Test", "msg", "WARNING");

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void testCreateForRole_DuplicateSuppressed() {
        when(notificationRepository.existsByTitleAndRecipientRoleAndIsReadFalse("Test", "ROLE_ADMIN"))
                .thenReturn(true);

        notificationService.createForRole("ROLE_ADMIN", "Test", "msg", "WARNING");

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void testNotifyAdminAndManager() {
        when(notificationRepository.existsByTitleAndRecipientRoleAndIsReadFalse(anyString(), anyString()))
                .thenReturn(false);

        notificationService.notifyAdminAndManager("Alert", "body", "DANGER");

        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    void testGetUnread() {
        Notification n = Notification.builder().title("T").recipientRole("ROLE_ADMIN").isRead(false).build();
        when(notificationRepository.findByRecipientRoleAndIsReadFalseOrderByCreatedAtDesc("ROLE_ADMIN"))
                .thenReturn(List.of(n));

        List<Notification> result = notificationService.getUnread("ROLE_ADMIN");

        assertEquals(1, result.size());
    }

    @Test
    void testGetUnreadCount_CacheMiss() {
        when(notificationRepository.countByRecipientRoleAndIsReadFalse("ROLE_ADMIN")).thenReturn(5L);

        long count = notificationService.getUnreadCount("ROLE_ADMIN");

        assertEquals(5, count);
        verify(notificationRepository).countByRecipientRoleAndIsReadFalse("ROLE_ADMIN");
    }

    @Test
    void testGetUnreadCount_CacheHit() {
        when(notificationRepository.countByRecipientRoleAndIsReadFalse("ROLE_ADMIN")).thenReturn(5L);

        // First call fills cache
        notificationService.getUnreadCount("ROLE_ADMIN");
        // Second call should use cache
        long count = notificationService.getUnreadCount("ROLE_ADMIN");

        assertEquals(5, count);
        // DB should only be called once
        verify(notificationRepository, times(1)).countByRecipientRoleAndIsReadFalse("ROLE_ADMIN");
    }

    @Test
    void testMarkAsRead_Found() {
        Notification n = Notification.builder().id(1L).isRead(false).recipientRole("ROLE_ADMIN").build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

        notificationService.markAsRead(1L);

        assertTrue(n.getIsRead());
        verify(notificationRepository).save(n);
    }

    @Test
    void testMarkAsRead_NotFound() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        notificationService.markAsRead(99L);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void testDismissAll() {
        notificationService.dismissAll("ROLE_ADMIN");

        verify(notificationRepository).markAllReadByRole("ROLE_ADMIN");
    }

    @Test
    void testGetAll() {
        when(notificationRepository.findByRecipientRoleOrderByCreatedAtDesc("ROLE_ADMIN"))
                .thenReturn(List.of());

        List<Notification> result = notificationService.getAll("ROLE_ADMIN");

        assertNotNull(result);
        verify(notificationRepository).findByRecipientRoleOrderByCreatedAtDesc("ROLE_ADMIN");
    }
}
