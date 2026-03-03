package thienloc.manage.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import thienloc.manage.entity.DailyProduction;
import thienloc.manage.entity.DailyProductionDetail;
import thienloc.manage.entity.User;
import thienloc.manage.repository.DailyProductionRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service
public class ExcelService {

    @Autowired
    private DailyProductionRepository productionRepository;

    @Autowired
    private UserService userService;

    // We need timeline slots from 07:00 to 21:00 (full hours)
    private static final List<String> TIME_SLOTS = Arrays.asList(
            "07:00-08:00", "08:00-09:00", "09:00-10:00", "10:00-11:00",
            "11:00-12:00", "12:00-13:00", "13:00-14:00", "14:00-15:00",
            "15:00-16:00", "16:00-17:00", "17:00-18:00", "18:00-19:00",
            "19:00-20:00", "20:00-21:00", "21:00-22:00");

    public ByteArrayInputStream generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Production Data");

            // Header row
            Row headerRow = sheet.createRow(0);
            String[] headers = { "Date", "Section", "Line", "MP", "WT", "Total Output" };

            // Core headers
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // Time slots headers. For each time slot we only need "Article"
            int colIndex = headers.length;
            for (String slot : TIME_SLOTS) {
                headerRow.createCell(colIndex++).setCellValue(slot + " (Article)");
            }

            // Sample data row
            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue(LocalDate.now().toString());
            dataRow.createCell(1).setCellValue("SEW");
            dataRow.createCell(2).setCellValue("1A");
            dataRow.createCell(3).setCellValue(30); // MP
            dataRow.createCell(4).setCellValue(10); // WT
            dataRow.createCell(5).setCellValue(1200); // Total Output

            // Fill sample data for some slots (indices 6 -> 20)
            dataRow.createCell(6).setCellValue("311872"); // 07:00 Article
            dataRow.createCell(7).setCellValue("311872"); // 08:00 Article
            dataRow.createCell(8).setCellValue("311872"); // 09:00 Article
            dataRow.createCell(9).setCellValue("311872"); // 10:00 Article
            // Leave 11:00 Blank (idx 10)
            // Leave 12:00 Blank (idx 11)
            dataRow.createCell(12).setCellValue("311872"); // 13:00 Article

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    public void importExcel(MultipartFile file, String username) throws IOException {
        User user = userService.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            // Start from row 1 (excluding header 0)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                Cell dateCell = row.getCell(0);
                if (dateCell == null || dateCell.getCellType() == CellType.BLANK)
                    continue;

                LocalDate productionDate;
                if (dateCell.getCellType() == CellType.NUMERIC) {
                    Date javaDate = DateUtil.getJavaDate(dateCell.getNumericCellValue());
                    productionDate = javaDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                } else {
                    productionDate = LocalDate.parse(dateCell.getStringCellValue());
                }

                String section = getCellValueAsString(row.getCell(1));
                String line = getCellValueAsString(row.getCell(2));
                Double mp = getCellValueAsDouble(row.getCell(3));
                Double wt = getCellValueAsDouble(row.getCell(4));
                Integer totalOutput = getCellValueAsInteger(row.getCell(5)); // New Column

                if (totalOutput == null) {
                    totalOutput = 0;
                }

                DailyProduction dailyProduction = DailyProduction.builder()
                        .productionDate(productionDate)
                        .section(section)
                        .line(line)
                        .mp(mp)
                        .wt(wt)
                        .createdBy(user)
                        .totalOutput(totalOutput)
                        .build();

                List<DailyProductionDetail> detailsList = new ArrayList<>();

                int colIndex = 6;
                for (String slot : TIME_SLOTS) {
                    Cell cell = row.getCell(colIndex++);
                    String article = getCellValueAsString(cell);

                    // We let 11 and 12 be parsed naturally if they enter something.
                    // If it's valid, we log it.
                    if (article != null && !article.trim().isEmpty()) {
                        DailyProductionDetail detail = DailyProductionDetail.builder()
                                .dailyProduction(dailyProduction)
                                .timeSlot(slot)
                                .output(0) // Logic changed: No longer need per-hour output in DB right now
                                .articleNo(article)
                                .build();
                        detailsList.add(detail);
                    }
                }

                dailyProduction.getDetails().addAll(detailsList);
                productionRepository.save(dailyProduction);
            }
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null)
            return null;
        if (cell.getCellType() == CellType.STRING)
            return cell.getStringCellValue();
        if (cell.getCellType() == CellType.NUMERIC)
            return String.valueOf((int) cell.getNumericCellValue());
        return null;
    }

    private Double getCellValueAsDouble(Cell cell) {
        if (cell == null)
            return null;
        if (cell.getCellType() == CellType.NUMERIC)
            return cell.getNumericCellValue();
        if (cell.getCellType() == CellType.STRING)
            return Double.parseDouble(cell.getStringCellValue());
        return null;
    }

    private Integer getCellValueAsInteger(Cell cell) {
        if (cell == null)
            return null;
        if (cell.getCellType() == CellType.NUMERIC)
            return (int) cell.getNumericCellValue();
        if (cell.getCellType() == CellType.STRING) {
            try {
                return Integer.parseInt(cell.getStringCellValue().trim());
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
