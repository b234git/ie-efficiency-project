package thienloc.manage.controller;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.util.Map;

/**
 * Dashboard hiển thị trạng thái hệ thống.
 * Chỉ ADMIN mới truy cập được (SecurityConfig: /admin/** → ADMIN).
 *
 * Spring Boot 4 đã bỏ HealthEndpoint/MetricsEndpoint khỏi actuator jar.
 * Ta dùng MeterRegistry (Micrometer) và DataSource trực tiếp.
 */
@Controller
@RequestMapping("/admin/system")
public class SystemHealthController {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private InfoEndpoint infoEndpoint;

    @Value("${info.app.name:IE-Eff}")
    private String appName;

    @Value("${info.app.version:1.0.0}")
    private String appVersion;

    @GetMapping
    public String systemHealth(Model model) {

        // ── Database health ─────────────────────────────────────────────────────
        String dbStatus = "DOWN";
        try (Connection conn = dataSource.getConnection()) {
            dbStatus = conn.isValid(2) ? "UP" : "DOWN";
        } catch (Exception ignored) {}
        model.addAttribute("dbStatus", dbStatus);

        // ── JVM Heap Memory ─────────────────────────────────────────────────────
        double heapUsed = gaugeSum("jvm.memory.used", "area", "heap");
        double heapMax  = gaugeSum("jvm.memory.max",  "area", "heap");
        if (heapMax > 0) {
            model.addAttribute("heapUsedMb",  mb(heapUsed));
            model.addAttribute("heapMaxMb",   mb(heapMax));
            model.addAttribute("heapPercent", percent(heapUsed, heapMax));
        } else {
            model.addAttribute("heapUsedMb",  mb(heapUsed));
            model.addAttribute("heapPercent", 0);
        }

        // ── Non-Heap Memory (Metaspace, CodeCache) ──────────────────────────────
        double nonHeapUsed = gaugeSum("jvm.memory.used", "area", "nonheap");
        model.addAttribute("nonHeapUsedMb", mb(nonHeapUsed));

        // ── CPU ─────────────────────────────────────────────────────────────────
        double sysCpu  = gaugeValue("system.cpu.usage");
        double procCpu = gaugeValue("process.cpu.usage");
        model.addAttribute("cpuPercent",        sysCpu  >= 0 ? (int)(sysCpu  * 100) : -1);
        model.addAttribute("processCpuPercent", procCpu >= 0 ? (int)(procCpu * 100) : -1);

        // ── Uptime ──────────────────────────────────────────────────────────────
        double uptimeSec = gaugeValue("process.uptime");
        model.addAttribute("uptime", uptimeSec >= 0 ? formatUptime((long) uptimeSec) : "N/A");

        // ── Threads ─────────────────────────────────────────────────────────────
        double threads = gaugeValue("jvm.threads.live");
        model.addAttribute("threadCount", threads >= 0 ? (int) threads : -1);

        // ── Disk Space (native Java, no Actuator needed) ────────────────────────
        File disk = new File(System.getProperty("user.dir"));
        long freeBytes  = disk.getUsableSpace();
        long totalBytes = disk.getTotalSpace();
        if (totalBytes > 0) {
            model.addAttribute("diskFree",    gb(freeBytes));
            model.addAttribute("diskTotal",   gb(totalBytes));
            model.addAttribute("diskPercent", percent(totalBytes - freeBytes, totalBytes));
            model.addAttribute("diskStatus",  freeBytes > 50L * 1024 * 1024 ? "OK" : "LOW");
        }

        // ── Overall status: UP only if DB is UP ─────────────────────────────────
        boolean allUp = "UP".equals(dbStatus);
        model.addAttribute("healthUp",     allUp);
        model.addAttribute("healthStatus", allUp ? "UP" : "DEGRADED");

        // ── App info ────────────────────────────────────────────────────────────
        model.addAttribute("appName",      appName);
        model.addAttribute("appVersion",   appVersion);
        model.addAttribute("javaRuntime",  System.getProperty("java.version"));

        // Try to get extra info from InfoEndpoint (still available in Boot 4)
        try {
            Map<String, Object> info = infoEndpoint.info();
            Object javaSection = info.get("java");
            if (javaSection instanceof Map<?, ?> javaMap) {
                model.addAttribute("javaVersion", javaMap.get("version"));
            }
        } catch (Exception ignored) {}

        return "system-health";
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Sum of all gauges matching name + tag filter (e.g. area:heap spans multiple IDs). */
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

    /** Single gauge value, -1 if not found. */
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
        long days  = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long mins  = (totalSeconds % 3600) / 60;
        long secs  = totalSeconds % 60;
        if (days  > 0) return days  + "d " + hours + "h " + mins + "m";
        if (hours > 0) return hours + "h " + mins  + "m " + secs + "s";
        return mins + "m " + secs + "s";
    }
}
