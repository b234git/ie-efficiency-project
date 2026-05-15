package thienloc.manage.controller.api;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system-health")
public class SystemHealthApiController {

    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;
    private final InfoEndpoint infoEndpoint;
    private final String appName;
    private final String appVersion;

    public SystemHealthApiController(
            MeterRegistry meterRegistry,
            DataSource dataSource,
            InfoEndpoint infoEndpoint,
            @Value("${info.app.name:IE-Eff}") String appName,
            @Value("${info.app.version:1.0.0}") String appVersion) {
        this.meterRegistry = meterRegistry;
        this.dataSource = dataSource;
        this.infoEndpoint = infoEndpoint;
        this.appName = appName;
        this.appVersion = appVersion;
    }

    @GetMapping
    public ResponseEntity<SystemHealthResponse> get() {
        String dbStatus = "DOWN";
        try (Connection conn = dataSource.getConnection()) {
            dbStatus = conn.isValid(2) ? "UP" : "DOWN";
        } catch (Exception ignored) {}

        double heapUsed = gaugeSum("jvm.memory.used", "area", "heap");
        double heapMax = gaugeSum("jvm.memory.max", "area", "heap");
        double nonHeapUsed = gaugeSum("jvm.memory.used", "area", "nonheap");
        double sysCpu = gaugeValue("system.cpu.usage");
        double procCpu = gaugeValue("process.cpu.usage");
        double uptimeSec = gaugeValue("process.uptime");
        double threads = gaugeValue("jvm.threads.live");

        File disk = new File(System.getProperty("user.dir"));
        long freeBytes = disk.getUsableSpace();
        long totalBytes = disk.getTotalSpace();
        String diskFree = totalBytes > 0 ? gb(freeBytes) : null;
        String diskTotal = totalBytes > 0 ? gb(totalBytes) : null;
        int diskPercent = totalBytes > 0 ? percent(totalBytes - freeBytes, totalBytes) : 0;
        String diskStatus = totalBytes > 0 ? (freeBytes > 50L * 1024 * 1024 ? "OK" : "LOW") : null;

        boolean allUp = "UP".equals(dbStatus);

        String javaVersion = null;
        try {
            Map<String, Object> info = infoEndpoint.info();
            Object javaSection = info.get("java");
            if (javaSection instanceof Map<?, ?> javaMap) {
                Object v = javaMap.get("version");
                if (v != null) javaVersion = v.toString();
            }
        } catch (Exception ignored) {}

        SystemHealthResponse body = new SystemHealthResponse(
                dbStatus,
                mb(heapUsed),
                heapMax > 0 ? mb(heapMax) : null,
                heapMax > 0 ? percent(heapUsed, heapMax) : 0,
                mb(nonHeapUsed),
                sysCpu >= 0 ? (int) (sysCpu * 100) : -1,
                procCpu >= 0 ? (int) (procCpu * 100) : -1,
                uptimeSec >= 0 ? formatUptime((long) uptimeSec) : "N/A",
                threads >= 0 ? (int) threads : -1,
                diskFree,
                diskTotal,
                diskPercent,
                diskStatus,
                allUp ? "UP" : "DEGRADED",
                allUp,
                appName,
                appVersion,
                System.getProperty("java.version"),
                javaVersion);
        return ResponseEntity.ok(body);
    }

    private double gaugeSum(String name, String tagKey, String tagValue) {
        try {
            return meterRegistry.find(name).tag(tagKey, tagValue)
                    .gauges().stream()
                    .mapToDouble(Gauge::value)
                    .filter(v -> v >= 0 && !Double.isNaN(v))
                    .sum();
        } catch (Exception e) {
            return 0;
        }
    }

    private double gaugeValue(String name) {
        try {
            Gauge gauge = meterRegistry.find(name).gauge();
            return gauge != null ? gauge.value() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private String mb(double bytes) {
        return String.format("%.0f MB", bytes / (1024.0 * 1024));
    }

    private String gb(long bytes) {
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private int percent(double part, double total) {
        return total > 0 ? (int) (part / total * 100) : 0;
    }

    private String formatUptime(long totalSeconds) {
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long mins = (totalSeconds % 3600) / 60;
        long secs = totalSeconds % 60;
        if (days > 0) return days + "d " + hours + "h " + mins + "m";
        if (hours > 0) return hours + "h " + mins + "m " + secs + "s";
        return mins + "m " + secs + "s";
    }

    public record SystemHealthResponse(
            String dbStatus,
            String heapUsedMb,
            String heapMaxMb,
            int heapPercent,
            String nonHeapUsedMb,
            int cpuPercent,
            int processCpuPercent,
            String uptime,
            int threadCount,
            String diskFree,
            String diskTotal,
            int diskPercent,
            String diskStatus,
            String healthStatus,
            boolean healthUp,
            String appName,
            String appVersion,
            String javaRuntime,
            String javaVersion) {}
}
