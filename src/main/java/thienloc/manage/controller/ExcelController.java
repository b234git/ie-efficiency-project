package thienloc.manage.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import thienloc.manage.dto.EntryImportPreviewDto;
import thienloc.manage.entity.ImportJob;
import thienloc.manage.service.IExcelService;
import thienloc.manage.service.ImportJobService;
import thienloc.manage.util.ExcelFileValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@Controller
@RequestMapping("/excel")
@RequiredArgsConstructor
public class ExcelController {

    private static final Logger log = LoggerFactory.getLogger(ExcelController.class);
    private static final String SESSION_PREVIEW = "entryImportPreview";
    private static final String SESSION_FILE = "entryImportFile";

    private final IExcelService excelService;

    private final ImportJobService importJobService;

    @GetMapping("/template")
    public ResponseEntity<InputStreamResource> downloadTemplate() throws IOException {
        ByteArrayInputStream in = excelService.generateTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=Production_Template.xlsx");

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    /**
     * Step 1: Parse file and show preview (no save yet).
     */
    @PostMapping("/preview")
    public String previewImport(@RequestParam("file") MultipartFile file,
                                HttpSession session,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("importError", "Please select a file to upload");
            return "redirect:/entry";
        }

        try {
            ExcelFileValidator.validate(file);
            EntryImportPreviewDto preview = excelService.parseForPreview(file);
            // Store file as temp file on disk to avoid holding large byte[] in session RAM
            Path tempFile = Files.createTempFile("ie-eff-import-", ".xlsx");
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            session.setAttribute(SESSION_FILE, tempFile.toString());
            session.setAttribute(SESSION_PREVIEW, preview);
            model.addAttribute("preview", preview);
            return "entry-import-confirm";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("importError", "Failed to parse file: " + e.getMessage());
            log.error("Failed to parse uploaded file", e);
            return "redirect:/entry";
        }
    }

    /**
     * Step 2: User confirms — kick off async import and redirect to status page.
     */
    @PostMapping("/import/confirm")
    public String confirmImport(HttpSession session,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        String filePath = (String) session.getAttribute(SESSION_FILE);
        if (filePath == null) {
            redirectAttributes.addFlashAttribute("error", "Session expired. Please upload the file again.");
            return "redirect:/entry";
        }
        // Clear session immediately — async task owns the temp file now
        session.removeAttribute(SESSION_FILE);
        session.removeAttribute(SESSION_PREVIEW);

        ImportJob job = importJobService.createJob("ENTRY_IMPORT", authentication.getName());
        importJobService.runEntryImport(job.getId(), filePath, authentication.getName());
        return "redirect:/excel/import/status/" + job.getId();
    }

    @GetMapping("/import/status/{id}")
    public String importStatus(@PathVariable Long id, Model model) {
        model.addAttribute("job", importJobService.findById(id));
        return "excel-import-status";
    }

    @GetMapping("/import/jobs/{id}/poll")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> pollJob(@PathVariable Long id) {
        ImportJob job = importJobService.findById(id);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("status", job.getStatus());
        body.put("progress", job.getProgress());
        body.put("errorMessage", job.getErrorMessage() != null ? job.getErrorMessage() : "");
        return ResponseEntity.ok(body);
    }

    /**
     * Cancel — clear session and go back.
     */
    @PostMapping("/import/cancel")
    public String cancelImport(HttpSession session) {
        String filePath = (String) session.getAttribute(SESSION_FILE);
        if (filePath != null) {
            try { Files.deleteIfExists(Path.of(filePath)); } catch (IOException ignored) {}
        }
        session.removeAttribute(SESSION_FILE);
        session.removeAttribute(SESSION_PREVIEW);
        return "redirect:/entry";
    }
}
