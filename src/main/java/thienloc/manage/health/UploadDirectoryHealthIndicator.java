package thienloc.manage.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component("uploadDirectoryHealth")
public class UploadDirectoryHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));

        if (!Files.exists(tmpDir) || !Files.isDirectory(tmpDir)) {
            return Health.down()
                    .withDetail("path", tmpDir.toString())
                    .withDetail("problem", "Thư mục không tồn tại")
                    .build();
        }

        if (!Files.isReadable(tmpDir) || !Files.isWritable(tmpDir)) {
            return Health.down()
                    .withDetail("path", tmpDir.toString())
                    .withDetail("readable", Files.isReadable(tmpDir))
                    .withDetail("writable", Files.isWritable(tmpDir))
                    .withDetail("problem", "Thư mục không có quyền đọc/ghi")
                    .build();
        }

        // Probe: thử tạo và xóa file tạm để xác nhận thực sự ghi được
        try {
            Path probe = Files.createTempFile(tmpDir, "health-probe-", ".tmp");
            Files.deleteIfExists(probe);
        } catch (IOException e) {
            return Health.down()
                    .withDetail("path", tmpDir.toString())
                    .withDetail("problem", "Không thể ghi file vào thư mục: " + e.getMessage())
                    .build();
        }

        return Health.up()
                .withDetail("path", tmpDir.toString())
                .withDetail("freeSpaceMB", tmpDir.toFile().getFreeSpace() / (1024 * 1024))
                .build();
    }
}
