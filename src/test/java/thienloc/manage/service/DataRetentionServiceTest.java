package thienloc.manage.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.MasterDbRepository;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataRetentionServiceTest {

    @Mock
    private MasterDbRepository masterDbRepository;

    @Mock
    private DailyProductionRepository dailyProductionRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SystemLogService systemLogService;

    @InjectMocks
    private DataRetentionService dataRetentionService;

    @Test
    void testCheckAndNotifyCreatesNotificationWhenDataExpiring() {
        // Simulate MasterDb records approaching expiration
        MasterDb old = MasterDb.builder().ref("OLD1").dataMonth("2024-01").build();
        when(masterDbRepository.findByDataMonthBefore(anyString()))
                .thenReturn(List.of(old));
        when(dailyProductionRepository.findByProductionDateBefore(any()))
                .thenReturn(Collections.emptyList());

        dataRetentionService.checkAndNotify();

        verify(notificationService).notifyAdminAndManager(
                contains("MasterDb"),
                contains("1"),
                eq("WARNING"));
    }

    @Test
    void testCheckAndNotifyNoNotificationWhenNoExpiringData() {
        when(masterDbRepository.findByDataMonthBefore(anyString()))
                .thenReturn(Collections.emptyList());
        when(dailyProductionRepository.findByProductionDateBefore(any()))
                .thenReturn(Collections.emptyList());

        dataRetentionService.checkAndNotify();

        verify(notificationService, never()).notifyAdminAndManager(anyString(), anyString(), anyString());
    }

    @Test
    void testDeleteExpiredDataDeletesOldRecords() {
        when(masterDbRepository.deleteByDataMonthBefore(anyString())).thenReturn(5);
        when(dailyProductionRepository.deleteByProductionDateBefore(any())).thenReturn(3);

        dataRetentionService.deleteExpiredData();

        verify(masterDbRepository).deleteByDataMonthBefore(anyString());
        verify(dailyProductionRepository).deleteByProductionDateBefore(any());
        verify(systemLogService, times(2)).logAction(eq("DATA_RETENTION"), anyString());
    }

    @Test
    void testDeleteExpiredDataNoLogWhenNothingDeleted() {
        when(masterDbRepository.deleteByDataMonthBefore(anyString())).thenReturn(0);
        when(dailyProductionRepository.deleteByProductionDateBefore(any())).thenReturn(0);

        dataRetentionService.deleteExpiredData();

        verify(systemLogService, never()).logAction(anyString(), anyString());
    }
}
