package thienloc.manage.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import thienloc.manage.entity.SystemLog;
import thienloc.manage.repository.SystemLogRepository;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemLogServiceTest {

    @Mock
    private SystemLogRepository systemLogRepository;

    @InjectMocks
    private SystemLogService systemLogService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testLogAction_AuthenticatedUser() {
        setUpSecurityContext("admin");

        systemLogService.logAction("TEST_ACTION", "some details");

        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository).save(captor.capture());
        assertEquals("admin", captor.getValue().getUsername());
        assertEquals("TEST_ACTION", captor.getValue().getAction());
        assertEquals("some details", captor.getValue().getDetails());
        assertNotNull(captor.getValue().getTimestamp());
    }

    @Test
    void testLogAction_AnonymousUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, Collections.emptyList()));

        systemLogService.logAction("ANON_ACTION", "anon details");

        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository).save(captor.capture());
        assertEquals("system", captor.getValue().getUsername());
    }

    @Test
    void testLogAction_NullAuthentication() {
        SecurityContextHolder.clearContext();

        systemLogService.logAction("NULL_AUTH", "no auth");

        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository).save(captor.capture());
        assertEquals("system", captor.getValue().getUsername());
    }

    @Test
    void testGetAllLogs() {
        List<SystemLog> logs = List.of(
                SystemLog.builder().action("A").build(),
                SystemLog.builder().action("B").build());
        when(systemLogRepository.findAllByOrderByTimestampDesc()).thenReturn(logs);

        List<SystemLog> result = systemLogService.getAllLogs();

        assertEquals(2, result.size());
    }

    @Test
    void testGetLogsPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<SystemLog> page = new PageImpl<>(List.of(SystemLog.builder().action("A").build()));
        when(systemLogRepository.findAllByOrderByTimestampDesc(pageable)).thenReturn(page);

        Page<SystemLog> result = systemLogService.getLogsPage(pageable);

        assertEquals(1, result.getTotalElements());
    }

    private void setUpSecurityContext(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
