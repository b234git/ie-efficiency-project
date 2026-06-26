package thienloc.manage.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import thienloc.manage.scheduler.DataRetentionScheduler;

import java.time.Duration;
import java.time.Instant;

@Component("schedulerHealth")
@RequiredArgsConstructor
public class SchedulerHealthIndicator implements HealthIndicator {

    private static final Duration MAX_SILENCE = Duration.ofHours(25);

    private final DataRetentionScheduler scheduler;

    @Override
    public Health health() {
        Instant lastRun = scheduler.getLastRun();

        if (lastRun == null) {
            return Health.up()
                    .withDetail("status", "Scheduler chưa chạy lần nào (app vừa khởi động)")
                    .build();
        }

        Duration sinceLastRun = Duration.between(lastRun, Instant.now());
        if (sinceLastRun.compareTo(MAX_SILENCE) <= 0) {
            return Health.up()
                    .withDetail("lastRun", lastRun.toString())
                    .withDetail("hoursSince", sinceLastRun.toHours())
                    .build();
        }

        return Health.down()
                .withDetail("lastRun", lastRun.toString())
                .withDetail("hoursSince", sinceLastRun.toHours())
                .withDetail("problem", "Scheduler không chạy trong hơn 25 giờ")
                .build();
    }
}
