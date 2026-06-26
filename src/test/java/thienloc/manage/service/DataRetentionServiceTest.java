package thienloc.manage.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import thienloc.manage.repository.DailyProductionRepository;
import thienloc.manage.repository.MasterDbRepository;
import thienloc.manage.repository.VocConsumptionRepository;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataRetentionServiceTest {

    @Mock
    private MasterDbRepository masterDbRepository;

    @Mock
    private DailyProductionRepository dailyProductionRepository;

    @Mock
    private VocConsumptionRepository vocConsumptionRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SystemLogService systemLogService;

    @InjectMocks
    private DataRetentionService dataRetentionService;

    @Test
    void testCheckAndNotifyCreatesNotificationWhenDataExpiring() {
        when(masterDbRepository.countByDataMonthBefore(anyString())).thenReturn(1L);
        when(dailyProductionRepository.countByProductionDateBefore(any())).thenReturn(0L);

        dataRetentionService.checkAndNotify();

        verify(notificationService).notifyAdminAndManager(
                contains("MasterDb"),
                contains("1"),
                eq("WARNING"));
    }

    @Test
    void testCheckAndNotifyNoNotificationWhenNoExpiringData() {
        // countBy methods default to 0L → no notification fired
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
