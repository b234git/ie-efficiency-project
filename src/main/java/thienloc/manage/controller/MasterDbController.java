package thienloc.manage.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import thienloc.manage.dto.ImportPreviewDto;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.service.MasterDbImportService;
import thienloc.manage.service.MasterDbService;
import thienloc.manage.service.MasterDbTemplateService;
import thienloc.manage.service.SystemLogService;
// import java.io.IOException;

@Controller
@RequestMapping("/masterdb")
public class MasterDbController {

    @Autowired
    private MasterDbService masterDbService;

    @Autowired
    private MasterDbImportService masterDbImportService;

    @Autowired
    private MasterDbTemplateService masterDbTemplateService;

    @Autowired
    private SystemLogService systemLogService;

    // ─── List (Read) ───────────────────────────────────────────────────────────

    @GetMapping({ "", "/" })
    public String list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dataMonth,
            Model model) {

        Page<MasterDb> resultPage = masterDbService.search(keyword, dataMonth, page);
        model.addAttribute("records", resultPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("dataMonth", dataMonth);
        model.addAttribute("availableMonths", masterDbService.getDistinctMonths());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", resultPage.getTotalPages());
        model.addAttribute("emptyRecord", new MasterDb());
        return "masterdb";
    }

    // ─── Save (Create / Update) ────────────────────────────────────────────────

    @PostMapping("/save")
    public String save(@ModelAttribute MasterDb entity,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            RedirectAttributes redirectAttributes) {
        try {
            boolean isNew = (entity.getId() == null);
            masterDbService.save(entity);
            String action = isNew ? "ADD_MASTERDB" : "EDIT_MASTERDB";
            systemLogService.logAction(action, "Ref: " + entity.getRef() + ", Article: " + entity.getArticleNo());
            redirectAttributes.addFlashAttribute("success",
                    isNew ? "Thêm mới thành công!" : "Cập nhật thành công!");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return buildRedirect(keyword, page);
    }

    // ─── Delete ────────────────────────────────────────────────────────────────

    @PostMapping("/delete")
    public String delete(@RequestParam Long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            RedirectAttributes redirectAttributes) {
        try {
            masterDbService.deleteById(id);
            systemLogService.logAction("DELETE_MASTERDB", "Deleted MasterDb ID: " + id);
            redirectAttributes.addFlashAttribute("success", "Xóa thành công!");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi xóa: " + e.getMessage());
        }
        return buildRedirect(keyword, page);
    }

    // ─── Import Excel (2-step: preview → confirm) ───────────────────────────────

    @PostMapping("/import")
    public String importExcel(@RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String dataMonth,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng chọn file Excel.");
            return "redirect:/masterdb/";
        }
        try {
            ImportPreviewDto preview = masterDbImportService.previewImport(file, dataMonth);
            session.setAttribute("importPreview", preview);
            return "redirect:/masterdb/import/confirm";
        } catch (IOException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi import: " + e.getMessage());
            return "redirect:/masterdb/";
        }
    }

    @GetMapping("/import/confirm")
    public String importConfirm(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        ImportPreviewDto preview = (ImportPreviewDto) session.getAttribute("importPreview");
        if (preview == null) {
            redirectAttributes.addFlashAttribute("error", "Không có dữ liệu import. Vui lòng upload lại.");
            return "redirect:/masterdb/";
        }
        model.addAttribute("preview", preview);
        return "masterdb-import-confirm";
    }

    @PostMapping("/import/commit")
    public String importCommit(HttpSession session,
            RedirectAttributes redirectAttributes) {
        ImportPreviewDto preview = (ImportPreviewDto) session.getAttribute("importPreview");
        if (preview == null) {
            redirectAttributes.addFlashAttribute("error", "Phiên import đã hết hạn. Vui lòng upload lại.");
            return "redirect:/masterdb/";
        }
        try {
            int count = masterDbImportService.commitImport(preview);
            session.removeAttribute("importPreview");
            systemLogService.logAction("IMPORT_MASTERDB",
                    "Imported " + count + " records (new: " + preview.getNewCount()
                            + ", updated: " + preview.getUpdateCount()
                            + "). Month: " + preview.getDataMonth());
            redirectAttributes.addFlashAttribute("success",
                    "Import thành công! " + preview.getNewCount() + " mới, "
                            + preview.getUpdateCount() + " cập nhật.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi commit import: " + e.getMessage());
        }
        return "redirect:/masterdb/";
    }

    @PostMapping("/import/cancel")
    public String importCancel(HttpSession session, RedirectAttributes redirectAttributes) {
        session.removeAttribute("importPreview");
        redirectAttributes.addFlashAttribute("success", "Đã hủy import.");
        return "redirect:/masterdb/";
    }

    // ─── Download Template ─────────────────────────────────────────────────────

    @GetMapping("/template")
    public ResponseEntity<InputStreamResource> downloadTemplate() throws IOException {
        byte[] bytes = masterDbTemplateService.generateTemplate();

        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.add("Content-Disposition", "attachment; filename=MasterDb_Template.xlsx");

        return ResponseEntity.ok()
                .headers(respHeaders)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(new ByteArrayInputStream(bytes)));
    }

    // ─── Helper ────────────────────────────────────────────────────────────────

    private String buildRedirect(String keyword, int page) {
        StringBuilder sb = new StringBuilder("redirect:/masterdb/?page=").append(page);
        if (keyword != null && !keyword.trim().isEmpty()) {
            sb.append("&keyword=").append(keyword);
        }
        return sb.toString();
    }
}
