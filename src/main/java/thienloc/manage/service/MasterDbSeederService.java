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

        // If the database already has data, assuming it's already seeded
        long count = masterDbRepository.count();
        // Force refresh during this phase to fix broken '.0' article data
        if (count > 0) {
            System.out.println("Clearing and re-running Excel import to apply format fix...");
            masterDbRepository.deleteAll();
        }

        String path = "c:/Users/mphat/Desktop/work/IE-Eff/info/EFF JAN V6.xlsx";
        File file = new File(path);

        if (!file.exists()) {
            System.out.println("Excel file not found at: " + path);
            return;
        }

        System.out.println("Starting Master Db Seed from Excel...");
        try (FileInputStream fis = new FileInputStream(file);
                Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheet("DB");
            if (sheet == null) {
                System.out.println("Sheet 'DB' not found in Excel");
                return;
            }

            int processedCount = 0;
            // Iterate rows starting from index 2 (Row 3 in Excel)
            for (int r = 2; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;

                Cell refCell = row.getCell(0);
                if (refCell == null || refCell.getCellType() == CellType.BLANK) {
                    continue; // Skip empty rows
                }

                String ref = getCellValueAsString(refCell);
                if (ref == null || ref.trim().isEmpty()) {
                    continue;
                }

                String articleNo = getCellValueAsString(row.getCell(1));
                if (articleNo == null || articleNo.trim().isEmpty()) {
                    continue; // Skip rows without an article number
                }

                String patternNo = getCellValueAsString(row.getCell(2));
                String shoeName = getCellValueAsString(row.getCell(3));
                String osCode = getCellValueAsString(row.getCell(4));
                Double sewQuota = getCellValueAsDouble(row.getCell(5));

                // Read the 8 PPH fields. Based on python checks earlier (0-indexed):
                // SEW (8), BUFFING 1ST (12), BUFFING 2ND (16), STOCKFIT UV (20)
                // STOCKFIT 1ST (24), STOCKFIT 2ND (28), ASSEMBLY BIG (32), ASSEMBLY SMALL (36)
                Double sewPph = getCellValueAsDouble(row.getCell(8));
                Double buff1stPph = getCellValueAsDouble(row.getCell(12));
                Double buff2ndPph = getCellValueAsDouble(row.getCell(16));
                Double stockfitUvPph = getCellValueAsDouble(row.getCell(20));
                Double stockfit1stPph = getCellValueAsDouble(row.getCell(24));
                Double stockfit2ndPph = getCellValueAsDouble(row.getCell(28));
                Double assemBigPph = getCellValueAsDouble(row.getCell(32));
                Double assemSmallPph = getCellValueAsDouble(row.getCell(36));

                // Check if already exists
                if (masterDbRepository.findByRef(ref).isPresent()) {
                    continue;
                }

                Double tct = null;
                // Assuming Quota is there:
                if (sewQuota != null && sewQuota > 0) {
                    tct = (10.0 * 3600.0) / sewQuota;
                }

                if (tct == null) {
                    tct = 0.0;
                }

                MasterDb dbEntry = MasterDb.builder()
                        .ref(ref)
                        .articleNo(articleNo)
                        .patternNo(patternNo)
                        .shoeName(shoeName)
                        .osCode(osCode)
                        .sewQuota(sewQuota)
                        .tct(tct)
                        .sewPph(sewPph)
                        .buff1stPph(buff1stPph)
                        .buff2ndPph(buff2ndPph)
                        .stockfitUvPph(stockfitUvPph)
                        .stockfit1stPph(stockfit1stPph)
                        .stockfit2ndPph(stockfit2ndPph)
                        .assemBigPph(assemBigPph)
                        .assemSmallPph(assemSmallPph)
                        .build();

                masterDbRepository.save(dbEntry);
                processedCount++;
            }
            System.out.println("Successfully seeded " + processedCount + " records into MasterDb.");

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
