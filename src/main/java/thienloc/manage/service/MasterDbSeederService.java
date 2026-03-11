package thienloc.manage.service;

import jakarta.annotation.PostConstruct;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import thienloc.manage.entity.MasterDb;
import thienloc.manage.repository.MasterDbRepository;

import java.io.File;
import java.io.FileInputStream;

@Service
public class MasterDbSeederService {

    @Autowired
    private MasterDbRepository masterDbRepository;

    @PostConstruct
    public void seedFromExcel() {
        // Override POI Zip bomb limit for large excel files
        IOUtils.setByteArrayMaxOverride(1000000000);

        // Use FEB as the latest source file; fall back to JAN if FEB not found
        String path = "c:/Users/mphat/Desktop/work/IE-Eff/info/EFF FEB V6.xlsx";
        File file = new File(path);
        if (!file.exists()) {
            path = "c:/Users/mphat/Desktop/work/IE-Eff/info/EFF JAN V6.xlsx";
            file = new File(path);
        }

        if (!file.exists()) {
            System.out.println("Excel file not found at: " + path);
            return;
        }

        System.out.println("Starting Master Db Seed/Upsert from: " + path);
        try (FileInputStream fis = new FileInputStream(file);
                Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheet("DB");
            if (sheet == null) {
                System.out.println("Sheet 'DB' not found in Excel");
                return;
            }

            int insertedCount = 0;
            int updatedCount = 0;

            // Iterate rows starting from index 2 (Row 3 in Excel — skip 2 header rows)
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

                // UPSERT: update if exists, insert if new
                java.util.Optional<MasterDb> existing = masterDbRepository.findByRef(ref);
                MasterDb entry;
                if (existing.isPresent()) {
                    entry = existing.get();
                } else {
                    entry = MasterDb.builder().ref(ref).build();
                }

                entry.setArticleNo(articleNo);
                entry.setPatternNo(patternNo);
                entry.setShoeName(shoeName);
                entry.setOsCode(osCode);

                entry.setSewCt(sewCt);
                entry.setSewMp(sewMp);
                entry.setSewQuotaDb(sewQuotaDb);
                entry.setSewPph(sewPph);

                entry.setBuff1stCt(buff1stCt);
                entry.setBuff1stMp(buff1stMp);
                entry.setBuff1stQuotaDb(buff1stQuotaDb);
                entry.setBuff1stPph(buff1stPph);

                entry.setBuff2ndCt(buff2ndCt);
                entry.setBuff2ndMp(buff2ndMp);
                entry.setBuff2ndQuotaDb(buff2ndQuotaDb);
                entry.setBuff2ndPph(buff2ndPph);

                entry.setStockfitUvCt(stockfitUvCt);
                entry.setStockfitUvMp(stockfitUvMp);
                entry.setStockfitUvQuotaDb(stockfitUvQuotaDb);
                entry.setStockfitUvPph(stockfitUvPph);

                entry.setStockfit1stCt(stockfit1stCt);
                entry.setStockfit1stMp(stockfit1stMp);
                entry.setStockfit1stQuotaDb(stockfit1stQuotaDb);
                entry.setStockfit1stPph(stockfit1stPph);

                entry.setStockfit2ndCt(stockfit2ndCt);
                entry.setStockfit2ndMp(stockfit2ndMp);
                entry.setStockfit2ndQuotaDb(stockfit2ndQuotaDb);
                entry.setStockfit2ndPph(stockfit2ndPph);

                entry.setAssemBigCt(assemBigCt);
                entry.setAssemBigMp(assemBigMp);
                entry.setAssemBigQuotaDb(assemBigQuotaDb);
                entry.setAssemBigPph(assemBigPph);

                entry.setAssemSmallCt(assemSmallCt);
                entry.setAssemSmallMp(assemSmallMp);
                entry.setAssemSmallQuotaDb(assemSmallQuotaDb);
                entry.setAssemSmallPph(assemSmallPph);

                if (existing.isPresent()) {
                    updatedCount++;
                } else {
                    insertedCount++;
                }
                masterDbRepository.save(entry);
            }
            System.out.println("MasterDb sync done: " + insertedCount + " inserted, " + updatedCount + " updated.");

        } catch (Exception e) {
            System.err.println("Failed to seed Master Db from Excel: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null)
            return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                double d = cell.getNumericCellValue();
                if (d == (long) d) {
                    return String.valueOf((long) d);
                }
                return String.valueOf(d);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    double fd = cell.getNumericCellValue();
                    if (fd == (long) fd) {
                        return String.valueOf((long) fd);
                    }
                    return String.valueOf(fd);
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
