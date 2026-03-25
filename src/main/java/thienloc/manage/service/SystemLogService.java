package thienloc.manage.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import thienloc.manage.entity.SystemLog;
import thienloc.manage.repository.SystemLogRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemLogService {

    private final SystemLogRepository systemLogRepository;

    public void logAction(String action, String details) {
        String username = "system"; // Mặc định

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            username = auth.getName();
        }

        SystemLog log = SystemLog.builder()
                .username(username)
                .action(action)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();

        systemLogRepository.save(log);
    }

    public List<SystemLog> getAllLogs() {
        return systemLogRepository.findAllByOrderByTimestampDesc();
    }

    public Page<SystemLog> getLogsPage(Pageable pageable) {
        return systemLogRepository.findAllByOrderByTimestampDesc(pageable);
    }
}
