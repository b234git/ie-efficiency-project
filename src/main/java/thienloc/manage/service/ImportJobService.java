package thienloc.manage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import thienloc.manage.entity.ImportJob;
import thienloc.manage.repository.ImportJobRepository;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Service
public class ImportJobService {

    private static final Logger log = LoggerFactory.getLogger(ImportJobService.class);

    @Autowired
    private ImportJobRepository importJobRepository;

    @Autowired
    private IExcelService excelService;

    public ImportJob createJob(String jobType, String username) {
        return importJobRepository.save(
                ImportJob.builder()
                        .jobType(jobType)
                        .status("PENDING")
                        .progress(0)
                        .createdBy(username)
                        .build());
    }

    public ImportJob findById(Long id) {
        return importJobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Import job not found: " + id));
    }

    public List<ImportJob> findRecentByUser(String username) {
        return importJobRepository.findTop10ByCreatedByOrderByCreatedAtDesc(username);
    }

    @Async("importExecutor")
    public void runEntryImport(Long jobId, String filePath, String username) {
        ImportJob job = importJobRepository.findById(jobId).orElseThrow();
        job.setStatus("PROCESSING");
        job.setUpdatedAt(Instant.now());
        importJobRepository.save(job);

        Path tempFile = Path.of(filePath);
        try (FileInputStream fis = new FileInputStream(tempFile.toFile())) {
            excelService.importExcel(fis, username);
            job.setStatus("DONE");
            job.setProgress(100);
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            log.error("Async entry import failed for job {}", jobId, e);
        } finally {
            job.setUpdatedAt(Instant.now());
            importJobRepository.save(job);
            try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
        }
    }
}
