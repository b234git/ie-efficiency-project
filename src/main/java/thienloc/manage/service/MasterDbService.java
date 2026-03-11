package thienloc.manage.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.repository.MasterDbRepository;

import java.io.IOException;
import java.util.Optional;

@Service
public class MasterDbService {

    private static final int PAGE_SIZE = 10;

    @Autowired
    private MasterDbRepository masterDbRepository;

    // ─── Read ──────────────────────────────────────────────────────────────────

    public Page<MasterDb> getAll(int page) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id").ascending());
        return masterDbRepository.findAll(pageable);
    }

    public Page<MasterDb> search(String keyword, int page) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAll(page);
        }
        Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("id").ascending());
        return masterDbRepository.findByRefContainingIgnoreCaseOrArticleNoContainingIgnoreCase(
                keyword, keyword, pageable);
    }

    public Optional<MasterDb> findById(Long id) {
        return masterDbRepository.findById(id);
    }

    // ─── Create / Update ───────────────────────────────────────────────────────

    public MasterDb save(MasterDb entity) {
        return masterDbRepository.save(entity);
    }

    // ─── Delete ────────────────────────────────────────────────────────────────

    public void deleteById(Long id) {
        masterDbRepository.deleteById(id);
    }

    // ─── Import Excel ──────────────────────────────────────────────────────────

    public int importFromExcel(MultipartFile file) throws IOException {
        IOUtils.setByteArrayMaxOverride(1_000_000_000);

        int importedCount = 0;
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {

            Sheet sheet = workbook.getSheet("DB");
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet 'DB' không tìm thấy trong file Excel.");
            }

            // Rows start at index 2 (Row 3 in Excel – skip 2 header rows)
            for (int r = 2; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;

                Cell refCell = row.getCell(0);
                if (refCell == null || refCell.getCellType() == CellType.BLANK)
                    continue;

                String ref = getCellValueAsString(refCell);
                if (ref == null || ref.trim().isEmpty())
                    continue;

                String articleNo = getCellValueAsString(row.getCell(1));
                if (articleNo == null || articleNo.trim().isEmpty())
                    continue;

                String patternNo = getCellValueAsString(row.getCell(2));
                String shoeName = getCellValueAsString(row.getCell(3));
                String osCode = getCellValueAsString(row.getCell(4));

                Double sewCt = getCellValueAsDouble(row.getCell(5));
                Double sewMp = getCellValueAsDouble(row.getCell(6));
                Double sewQuotaDb = getCellValueAsDouble(row.getCell(7));
                Double sewPph = getCellValueAsDouble(row.getCell(8));

                Double buff1stCt = getCellValueAsDouble(row.getCell(9));
                Double buff1stMp = getCellValueAsDouble(row.getCell(10));
                Double buff1stQuotaDb = getCellValueAsDouble(row.getCell(11));
                Double buff1stPph = getCellValueAsDouble(row.getCell(12));

                Double buff2ndCt = getCellValueAsDouble(row.getCell(13));
                Double buff2ndMp = getCellValueAsDouble(row.getCell(14));
                Double buff2ndQuotaDb = getCellValueAsDouble(row.getCell(15));
                Double buff2ndPph = getCellValueAsDouble(row.getCell(16));

                Double stockfitUvCt = getCellValueAsDouble(row.getCell(17));
                Double stockfitUvMp = getCellValueAsDouble(row.getCell(18));
                Double stockfitUvQuotaDb = getCellValueAsDouble(row.getCell(19));
                Double stockfitUvPph = getCellValueAsDouble(row.getCell(20));

                Double stockfit1stCt = getCellValueAsDouble(row.getCell(21));
                Double stockfit1stMp = getCellValueAsDouble(row.getCell(22));
                Double stockfit1stQuotaDb = getCellValueAsDouble(row.getCell(23));
                Double stockfit1stPph = getCellValueAsDouble(row.getCell(24));

                Double stockfit2ndCt = getCellValueAsDouble(row.getCell(25));
                Double stockfit2ndMp = getCellValueAsDouble(row.getCell(26));
                Double stockfit2ndQuotaDb = getCellValueAsDouble(row.getCell(27));
                Double stockfit2ndPph = getCellValueAsDouble(row.getCell(28));

                Double assemBigCt = getCellValueAsDouble(row.getCell(29));
                Double assemBigMp = getCellValueAsDouble(row.getCell(30));
                Double assemBigQuotaDb = getCellValueAsDouble(row.getCell(31));
                Double assemBigPph = getCellValueAsDouble(row.getCell(32));

                Double assemSmallCt = getCellValueAsDouble(row.getCell(33));
                Double assemSmallMp = getCellValueAsDouble(row.getCell(34));
                Double assemSmallQuotaDb = getCellValueAsDouble(row.getCell(35));
                Double assemSmallPph = getCellValueAsDouble(row.getCell(36));

                // Upsert: update if ref already exists, otherwise create new
                MasterDb entity = masterDbRepository.findByRef(ref)
                        .orElse(MasterDb.builder().ref(ref).build());

                entity.setArticleNo(articleNo);
                entity.setPatternNo(patternNo);
                entity.setShoeName(shoeName);
                entity.setOsCode(osCode);

                entity.setSewCt(sewCt);
                entity.setSewMp(sewMp);
                entity.setSewQuotaDb(sewQuotaDb);
                entity.setSewPph(sewPph);

                entity.setBuff1stCt(buff1stCt);
                entity.setBuff1stMp(buff1stMp);
                entity.setBuff1stQuotaDb(buff1stQuotaDb);
                entity.setBuff1stPph(buff1stPph);

                entity.setBuff2ndCt(buff2ndCt);
                entity.setBuff2ndMp(buff2ndMp);
                entity.setBuff2ndQuotaDb(buff2ndQuotaDb);
                entity.setBuff2ndPph(buff2ndPph);

                entity.setStockfitUvCt(stockfitUvCt);
                entity.setStockfitUvMp(stockfitUvMp);
                entity.setStockfitUvQuotaDb(stockfitUvQuotaDb);
                entity.setStockfitUvPph(stockfitUvPph);

                entity.setStockfit1stCt(stockfit1stCt);
                entity.setStockfit1stMp(stockfit1stMp);
                entity.setStockfit1stQuotaDb(stockfit1stQuotaDb);
                entity.setStockfit1stPph(stockfit1stPph);

                entity.setStockfit2ndCt(stockfit2ndCt);
                entity.setStockfit2ndMp(stockfit2ndMp);
                entity.setStockfit2ndQuotaDb(stockfit2ndQuotaDb);
                entity.setStockfit2ndPph(stockfit2ndPph);

                entity.setAssemBigCt(assemBigCt);
                entity.setAssemBigMp(assemBigMp);
                entity.setAssemBigQuotaDb(assemBigQuotaDb);
                entity.setAssemBigPph(assemBigPph);

                entity.setAssemSmallCt(assemSmallCt);
                entity.setAssemSmallMp(assemSmallMp);
                entity.setAssemSmallQuotaDb(assemSmallQuotaDb);
                entity.setAssemSmallPph(assemSmallPph);

                masterDbRepository.save(entity);
                importedCount++;
            }
        }
        return importedCount;
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private String getCellValueAsString(Cell cell) {
        if (cell == null)
            return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC: {
                double d = cell.getNumericCellValue();
                return (d == (long) d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA: {
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    double fd = cell.getNumericCellValue();
                    return (fd == (long) fd) ? String.valueOf((long) fd) : String.valueOf(fd);
                }
            }
            default:
                return null;
        }
    }

    private Double getCellValueAsDouble(Cell cell) {
        if (cell == null)
            return null;
        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                case FORMULA:
                    return cell.getNumericCellValue();
                case STRING:
                    return Double.parseDouble(cell.getStringCellValue());
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
