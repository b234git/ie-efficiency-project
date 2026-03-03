package thienloc.manage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import thienloc.manage.service.ExcelService;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Controller
@RequestMapping("/excel")
public class ExcelController {

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

    @PostMapping("/import")
    public String importExcel(@RequestParam("file") MultipartFile file, Authentication authentication,
            RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
            return "redirect:/entry";
        }

        try {
            excelService.importExcel(file, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "File imported successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to import file: " + e.getMessage());
            e.printStackTrace();
        }

        return "redirect:/entry";
    }
}
