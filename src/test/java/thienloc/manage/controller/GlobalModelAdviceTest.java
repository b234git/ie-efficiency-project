package thienloc.manage.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import thienloc.manage.service.NotificationService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalModelAdviceTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private GlobalModelAdvice globalModelAdvice;

    @Test
    void testNotificationCount_NullAuth() {
        long count = globalModelAdvice.notificationCount(null);
        assertEquals(0, count);
    }

    @Test
    void testNotificationCount_NotAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        long count = globalModelAdvice.notificationCount(auth);
        assertEquals(0, count);
    }

    @Test
    void testNotificationCount_AdminRole() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(auth).getAuthorities();
        when(notificationService.getUnreadCount("ROLE_ADMIN")).thenReturn(5L);

        long count = globalModelAdvice.notificationCount(auth);
        assertEquals(5, count);
    }

    @Test
    void testNotificationCount_ManagerRole() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))).when(auth).getAuthorities();
        when(notificationService.getUnreadCount("ROLE_MANAGER")).thenReturn(3L);

        long count = globalModelAdvice.notificationCount(auth);
        assertEquals(3, count);
    }

    @Test
    void testNotificationCount_UserRole() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(auth).getAuthorities();

        long count = globalModelAdvice.notificationCount(auth);
        assertEquals(0, count);
    }
}
