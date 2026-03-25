package thienloc.manage.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
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
import thienloc.manage.service.ExcelService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Controller
@RequestMapping("/excel")
public class ExcelController {

    private static final Logger log = LoggerFactory.getLogger(ExcelController.class);
    private static final String SESSION_PREVIEW = "entryImportPreview";
    private static final String SESSION_FILE = "entryImportFile";

    @Autowired
    private ExcelService excelService;

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
            redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
            return "redirect:/entry";
        }

        try {
            EntryImportPreviewDto preview = excelService.parseForPreview(file);
            // Store file bytes in session so we can import on confirm
            session.setAttribute(SESSION_FILE, file.getBytes());
            session.setAttribute(SESSION_PREVIEW, preview);
            model.addAttribute("preview", preview);
            return "entry-import-confirm";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to parse file: " + e.getMessage());
            log.error("Failed to parse uploaded file", e);
            return "redirect:/entry";
        }
    }

    /**
     * Step 2: User confirms — actually import the data.
     */
    @PostMapping("/import/confirm")
    public String confirmImport(HttpSession session,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        byte[] fileBytes = (byte[]) session.getAttribute(SESSION_FILE);
        if (fileBytes == null) {
            redirectAttributes.addFlashAttribute("error", "Session expired. Please upload the file again.");
            return "redirect:/entry";
        }

        try {
            excelService.importExcel(fileBytes, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "File imported successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to import file: " + e.getMessage());
            log.error("Failed to import file", e);
        } finally {
            session.removeAttribute(SESSION_FILE);
            session.removeAttribute(SESSION_PREVIEW);
        }

        return "redirect:/entry";
    }

    /**
     * Cancel — clear session and go back.
     */
    @PostMapping("/import/cancel")
    public String cancelImport(HttpSession session) {
        session.removeAttribute(SESSION_FILE);
        session.removeAttribute(SESSION_PREVIEW);
        return "redirect:/entry";
    }
}
