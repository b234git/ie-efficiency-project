package thienloc.manage.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import thienloc.manage.dto.ImportPreviewDto;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.repository.MasterDbRepository;
import thienloc.manage.util.ExcelCellUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles Excel import, preview (duplicate detection), and strategy-based commit
 * for MasterDb records. Extracted from MasterDbService to keep file sizes under 250 lines.
 */
@Service
public class MasterDbImportService {

    @Autowired
    private MasterDbRepository masterDbRepository;

    @Autowired
    private MasterDbService masterDbService;

    @Autowired
    private MeterRegistry meterRegistry;

    // ─── Direct Import (backward compat) ────────────────────────────────────

    public int importFromExcel(MultipartFile file) throws IOException {
        return importFromExcel(file, null);
    }

    public int importFromExcel(MultipartFile file, String dataMonth) throws IOException {
        long t0 = System.nanoTime();
        IOUtils.setByteArrayMaxOverride(100_000_000); // 100MB max

        // Pre-load all existing records once instead of querying per row
        Map<String, MasterDb> existingMap = loadExistingAsMap(dataMonth);

        List<MasterDb> toSave = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = getDbSheet(workbook);

            for (int r = 2; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String ref = getRef(row);
                if (ref == null) continue;

                String articleNo = getCellValueAsString(row.getCell(1));
                if (articleNo == null || articleNo.trim().isEmpty()) continue;

                MasterDb entity = existingMap.getOrDefault(ref, buildNewEntity(ref, dataMonth));
                populateEntityFromRow(entity, articleNo, row);
                toSave.add(entity);
            }
        }

        // Batch save — routes through MasterDbService to trigger cache eviction
        masterDbService.saveAll(toSave);
        meterRegistry.timer("excel.masterdb.import").record(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
        return toSave.size();
    }

    // ─── Preview Import (Duplicate Detection) ───────────────────────────────

    public ImportPreviewDto previewImport(MultipartFile file, String dataMonth) throws IOException {
        IOUtils.setByteArrayMaxOverride(100_000_000); // 100MB max

        ImportPreviewDto preview = ImportPreviewDto.builder()
                .dataMonth(dataMonth)
                .filename(file.getOriginalFilename())
                .rows(new ArrayList<>())
                .build();

        // Pre-load all existing records once instead of querying per row
        Map<String, MasterDb> existingMap = loadExistingAsMap(dataMonth);

        int newCount = 0, updateCount = 0;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = getDbSheet(workbook);

            for (int r = 2; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String ref = getRef(row);
                if (ref == null) continue;

                String articleNo = getCellValueAsString(row.getCell(1));
                if (articleNo == null || articleNo.trim().isEmpty()) continue;

                MasterDb existing = existingMap.get(ref);
                boolean isUpdate = existing != null;
                MasterDb entity = (existing != null) ? existing : buildNewEntity(ref, dataMonth);

                populateEntityFromRow(entity, articleNo, row);

                if (isUpdate) updateCount++; else newCount++;

                preview.getRows().add(ImportPreviewDto.ImportRowPreview.builder()
                        .ref(ref).articleNo(articleNo)
                        .patternNo(entity.getPatternNo()).shoeName(entity.getShoeName())
                        .osCode(entity.getOsCode())
                        .status(isUpdate ? "UPDATE" : "NEW")
                        .entity(entity)
                        .build());
            }
        }

        preview.setTotalRows(newCount + updateCount);
        preview.setNewCount(newCount);
        preview.setUpdateCount(updateCount);
        return preview;
    }

    // ─── Commit with Strategy ───────────────────────────────────────────────

    public int commitImport(ImportPreviewDto preview) {
        List<MasterDb> toSave = preview.getRows().stream()
                .map(ImportPreviewDto.ImportRowPreview::getEntity)
                .collect(Collectors.toList());
        masterDbService.saveAll(toSave);
        return toSave.size();
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Map<String, MasterDb> loadExistingAsMap(String dataMonth) {
        List<MasterDb> existing = (dataMonth != null && !dataMonth.trim().isEmpty())
                ? masterDbRepository.findByDataMonth(dataMonth)
                : masterDbRepository.findAll();
        return existing.stream().collect(Collectors.toMap(MasterDb::getRef, m -> m, (a, b) -> a));
    }

    private MasterDb buildNewEntity(String ref, String dataMonth) {
        MasterDb.MasterDbBuilder builder = MasterDb.builder().ref(ref);
        if (dataMonth != null && !dataMonth.trim().isEmpty()) {
            builder.dataMonth(dataMonth);
        }
        return builder.build();
    }

    private Sheet getDbSheet(Workbook workbook) {
        Sheet sheet = workbook.getSheet("DB");
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet 'DB' not found in Excel file.");
        }
        return sheet;
    }

    private String getRef(Row row) {
        Cell refCell = row.getCell(0);
        if (refCell == null || refCell.getCellType() == CellType.BLANK) return null;
        String ref = getCellValueAsString(refCell);
        return (ref != null && !ref.trim().isEmpty()) ? ref : null;
    }

    @SuppressWarnings("unused") // kept for backward compat reference
    private MasterDb findOrCreateEntity(String ref, String dataMonth) {
        if (dataMonth != null && !dataMonth.trim().isEmpty()) {
            return masterDbRepository.findByRefAndDataMonth(ref, dataMonth)
                    .orElse(MasterDb.builder().ref(ref).dataMonth(dataMonth).build());
        }
        return masterDbRepository.findByRef(ref)
                .orElse(MasterDb.builder().ref(ref).build());
    }

    private void populateEntityFromRow(MasterDb entity, String articleNo, Row row) {
        entity.setArticleNo(articleNo);
        entity.setPatternNo(getCellValueAsString(row.getCell(2)));
        entity.setShoeName(getCellValueAsString(row.getCell(3)));
        entity.setOsCode(getCellValueAsString(row.getCell(4)));

        entity.setSewCt(getCellValueAsDouble(row.getCell(5)));
        entity.setSewMp(getCellValueAsDouble(row.getCell(6)));
        entity.setSewQuotaDb(getCellValueAsDouble(row.getCell(7)));
        entity.setSewPph(getCellValueAsDouble(row.getCell(8)));

        entity.setBuff1stCt(getCellValueAsDouble(row.getCell(9)));
        entity.setBuff1stMp(getCellValueAsDouble(row.getCell(10)));
        entity.setBuff1stQuotaDb(getCellValueAsDouble(row.getCell(11)));
        entity.setBuff1stPph(getCellValueAsDouble(row.getCell(12)));

        entity.setBuff2ndCt(getCellValueAsDouble(row.getCell(13)));
        entity.setBuff2ndMp(getCellValueAsDouble(row.getCell(14)));
        entity.setBuff2ndQuotaDb(getCellValueAsDouble(row.getCell(15)));
        entity.setBuff2ndPph(getCellValueAsDouble(row.getCell(16)));

        entity.setStockfitUvCt(getCellValueAsDouble(row.getCell(17)));
        entity.setStockfitUvMp(getCellValueAsDouble(row.getCell(18)));
        entity.setStockfitUvQuotaDb(getCellValueAsDouble(row.getCell(19)));
        entity.setStockfitUvPph(getCellValueAsDouble(row.getCell(20)));

        entity.setStockfit1stCt(getCellValueAsDouble(row.getCell(21)));
        entity.setStockfit1stMp(getCellValueAsDouble(row.getCell(22)));
        entity.setStockfit1stQuotaDb(getCellValueAsDouble(row.getCell(23)));
        entity.setStockfit1stPph(getCellValueAsDouble(row.getCell(24)));

        entity.setStockfit2ndCt(getCellValueAsDouble(row.getCell(25)));
        entity.setStockfit2ndMp(getCellValueAsDouble(row.getCell(26)));
        entity.setStockfit2ndQuotaDb(getCellValueAsDouble(row.getCell(27)));
        entity.setStockfit2ndPph(getCellValueAsDouble(row.getCell(28)));

        entity.setAssemBigCt(getCellValueAsDouble(row.getCell(29)));
        entity.setAssemBigMp(getCellValueAsDouble(row.getCell(30)));
        entity.setAssemBigQuotaDb(getCellValueAsDouble(row.getCell(31)));
        entity.setAssemBigPph(getCellValueAsDouble(row.getCell(32)));

        entity.setAssemSmallCt(getCellValueAsDouble(row.getCell(33)));
        entity.setAssemSmallMp(getCellValueAsDouble(row.getCell(34)));
        entity.setAssemSmallQuotaDb(getCellValueAsDouble(row.getCell(35)));
        entity.setAssemSmallPph(getCellValueAsDouble(row.getCell(36)));
    }

    String getCellValueAsString(Cell cell) {
        return ExcelCellUtil.getString(cell);
    }

    Double getCellValueAsDouble(Cell cell) {
        return ExcelCellUtil.getDouble(cell);
    }
}
