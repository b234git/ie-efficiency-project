package thienloc.manage.service;

import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import thienloc.manage.dto.WeeklyImportResultDto;
import thienloc.manage.entity.ReprocessRecord;
import thienloc.manage.entity.SixSRecord;
import thienloc.manage.repository.ReprocessRecordRepository;
import thienloc.manage.repository.SixSRecordRepository;

import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class WeeklyTrackingService {

    @Autowired
    private SixSRecordRepository sixsRepo;

    @Autowired
    private ReprocessRecordRepository reproRepo;

    // ── Section normalization ─────────────────────────────────────────────────

    public static String normalizeSection(String section) {
        if (section == null) return null;
        String s = section.trim().toUpperCase();
        if (s.startsWith("ASSEMBLY") || s.startsWith("ASSY")) return "ASSEMBLY";
        if (s.startsWith("STOCKFIT") || s.startsWith("SF"))   return "STOCKFIT";
        if (s.startsWith("BUFFING")  || s.startsWith("BUFF")) return "BUFFING";
        return s;
    }

    // ── 6S Score ──────────────────────────────────────────────────────────────

    public List<SixSRecord> getSixSByMonth(String dataMonth) {
        List<SixSRecord> list = sixsRepo.findByDataMonthOrderBySectionAscLineAsc(dataMonth);
        list.forEach(r -> r.setSection(normalizeSection(r.getSection())));
        return list;
    }

    public List<String> getDistinctSixSMonths() {
        return sixsRepo.findDistinctDataMonths();
    }

    public Optional<SixSRecord> findSixSById(Long id) {
        return sixsRepo.findById(id);
    }

    public void saveSixS(SixSRecord record) {
        record.setSection(normalizeSection(record.getSection()));
        sixsRepo.save(record);
    }

    public void deleteSixS(Long id) {
        sixsRepo.deleteById(id);
    }

    @Transactional
    public void deleteSixSByIds(List<Long> ids) {
        sixsRepo.deleteAllById(ids);
    }

    public List<SixSRecord> getAllSixS() {
        List<SixSRecord> list = sixsRepo.findAll(
                Sort.by(Sort.Direction.DESC, "dataMonth")
                        .and(Sort.by("section")).and(Sort.by("line")));
        list.forEach(r -> r.setSection(normalizeSection(r.getSection())));
        return list;
    }

    // ── Reprocess ─────────────────────────────────────────────────────────────

    public List<ReprocessRecord> getReprocessByMonth(String dataMonth) {
        List<ReprocessRecord> list = reproRepo.findByDataMonthOrderBySectionAscLineAsc(dataMonth);
        list.forEach(r -> r.setSection(normalizeSection(r.getSection())));
        return list;
    }

    public List<String> getDistinctReprocessMonths() {
        return reproRepo.findDistinctDataMonths();
    }

    public Optional<ReprocessRecord> findReprocessById(Long id) {
        return reproRepo.findById(id);
    }

    public void saveReprocess(ReprocessRecord record) {
        record.setSection(normalizeSection(record.getSection()));
        reproRepo.save(record);
    }

    public void deleteReprocess(Long id) {
        reproRepo.deleteById(id);
    }

    @Transactional
    public void deleteReprocessByIds(List<Long> ids) {
        reproRepo.deleteAllById(ids);
    }

    public List<ReprocessRecord> getAllReprocess() {
        List<ReprocessRecord> list = reproRepo.findAll(
                Sort.by(Sort.Direction.DESC, "dataMonth")
                        .and(Sort.by("section")).and(Sort.by("line")));
        list.forEach(r -> r.setSection(normalizeSection(r.getSection())));
        return list;
    }

    // ── Combined month list (union of both tables) ────────────────────────────

    public List<String> getAllDistinctMonths() {
        List<String> months = new ArrayList<>(getDistinctSixSMonths());
        for (String m : getDistinctReprocessMonths()) {
            if (!months.contains(m)) months.add(m);
        }
        months.sort((a, b) -> b.compareTo(a));
        return months;
    }

    // ── Excel Import: 6S ──────────────────────────────────────────────────────

    @Transactional
    public WeeklyImportResultDto importSixSFromExcel(MultipartFile file, String month) throws IOException {
        WeeklyImportResultDto result = new WeeklyImportResultDto();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) { result.setSkipped(result.getSkipped() + 1); continue; }

                String section = getCellString(row.getCell(0));
                String line    = getCellString(row.getCell(1));

                if ((section == null || section.isBlank()) && (line == null || line.isBlank())) {
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }
                if (section == null || section.isBlank()) {
                    result.getErrors().add("Row " + (i + 1) + ": Section is required");
                    continue;
                }
                if (line == null || line.isBlank()) {
                    result.getErrors().add("Row " + (i + 1) + ": Line is required");
                    continue;
                }
                section = normalizeSection(section);
                line = line.trim();

                Integer[] weeks = new Integer[5];
                boolean hasError = false;
                for (int w = 0; w < 5; w++) {
                    Integer val = getCellInteger(row.getCell(2 + w));
                    if (val != null && val < 0) {
                        result.getErrors().add("Row " + (i + 1) + ": Week" + (w + 1) + " cannot be negative");
                        hasError = true;
                        break;
                    }
                    weeks[w] = val;
                }
                if (hasError) continue;

                Optional<SixSRecord> existing = sixsRepo.findByDataMonthAndSectionAndLine(month, section, line);
                SixSRecord record = existing.orElseGet(SixSRecord::new);
                record.setDataMonth(month);
                record.setSection(section);
                record.setLine(line);
                record.setWeek1(weeks[0]);
                record.setWeek2(weeks[1]);
                record.setWeek3(weeks[2]);
                record.setWeek4(weeks[3]);
                record.setWeek5(weeks[4]);
                sixsRepo.save(record);

                if (existing.isPresent()) result.setUpdated(result.getUpdated() + 1);
                else                      result.setInserted(result.getInserted() + 1);
            }
        }
        return result;
    }

    // ── Excel Import: Reprocess ───────────────────────────────────────────────

    @Transactional
    public WeeklyImportResultDto importReprocessFromExcel(MultipartFile file, String month) throws IOException {
        WeeklyImportResultDto result = new WeeklyImportResultDto();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) { result.setSkipped(result.getSkipped() + 1); continue; }

                String section = getCellString(row.getCell(0));
                String line    = getCellString(row.getCell(1));

                if ((section == null || section.isBlank()) && (line == null || line.isBlank())) {
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }
                if (section == null || section.isBlank()) {
                    result.getErrors().add("Row " + (i + 1) + ": Section is required");
                    continue;
                }
                if (line == null || line.isBlank()) {
                    result.getErrors().add("Row " + (i + 1) + ": Line is required");
                    continue;
                }
                section = normalizeSection(section);
                line = line.trim();

                Integer[] weeks = new Integer[5];
                boolean hasError = false;
                for (int w = 0; w < 5; w++) {
                    Integer val = getCellInteger(row.getCell(2 + w));
                    if (val != null && val < 0) {
                        result.getErrors().add("Row " + (i + 1) + ": Week" + (w + 1) + " cannot be negative");
                        hasError = true;
                        break;
                    }
                    weeks[w] = val;
                }
                if (hasError) continue;

                Integer output = getCellInteger(row.getCell(7));
                if (output != null && output < 0) {
                    result.getErrors().add("Row " + (i + 1) + ": Output cannot be negative");
                    continue;
                }

                Optional<ReprocessRecord> existing = reproRepo.findByDataMonthAndSectionAndLine(month, section, line);
                ReprocessRecord record = existing.orElseGet(ReprocessRecord::new);
                record.setDataMonth(month);
                record.setSection(section);
                record.setLine(line);
                record.setWeek1(weeks[0]);
                record.setWeek2(weeks[1]);
                record.setWeek3(weeks[2]);
                record.setWeek4(weeks[3]);
                record.setWeek5(weeks[4]);
                record.setOutput(output);
                reproRepo.save(record);

                if (existing.isPresent()) result.setUpdated(result.getUpdated() + 1);
                else                      result.setInserted(result.getInserted() + 1);
            }
        }
        return result;
    }

    // ── Cell parsing helpers ──────────────────────────────────────────────────

    private String getCellString(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
        if (cell.getCellType() == CellType.STRING)  return cell.getStringCellValue();
        return null;
    }

    private Integer getCellInteger(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) return (int) cell.getNumericCellValue();
        if (cell.getCellType() == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            if (s.isEmpty()) return null;
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
