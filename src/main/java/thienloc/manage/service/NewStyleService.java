package thienloc.manage.service;

import org.apache.poi.ss.usermodel.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import thienloc.manage.entity.NewStyleEntry;
import thienloc.manage.repository.NewStyleEntryRepository;
import thienloc.manage.util.ExcelCellUtil;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NewStyleService {

    private final NewStyleEntryRepository repo;

    public List<NewStyleEntry> getAll() {
        return repo.findAllByOrderBySectionAscLineAscStyleAsc();
    }

    public List<NewStyleEntry> getByMonth(String dataMonth) {
        return repo.findByDataMonthOrderBySectionAscLineAscStyleAsc(dataMonth);
    }

    public Optional<NewStyleEntry> findById(Long id) {
        return repo.findById(id);
    }

    public NewStyleEntry save(NewStyleEntry entry) {
        return repo.save(entry);
    }

    public void deleteById(Long id) {
        repo.deleteById(id);
    }

    public int importFromExcel(MultipartFile file, String dataMonth) throws IOException {
        int count = 0;
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // Support both old format (4 cols: Section,Line,Style,Qty)
                // and new format (5 cols: DataMonth,Section,Line,Style,Qty)
                int colOffset = 0;
                String firstCell = getCellString(row.getCell(0));
                if (firstCell != null && firstCell.matches("\\d{4}-\\d{2}")) {
                    dataMonth  = firstCell;
                    colOffset  = 1;
                }

                String section = getCellString(row.getCell(colOffset));
                String line    = getCellString(row.getCell(colOffset + 1));
                String style   = getCellString(row.getCell(colOffset + 2));
                Integer qty    = getCellInteger(row.getCell(colOffset + 3));

                if (section == null || section.isBlank() ||
                    line    == null || line.isBlank()    ||
                    style   == null || style.isBlank()   ||
                    qty == null || qty < 1) continue;

                NewStyleEntry entry = new NewStyleEntry();
                entry.setDataMonth(dataMonth);
                entry.setSection(section.trim().toUpperCase());
                entry.setLine(line.trim());
                entry.setStyle(style.trim());
                entry.setQuantity(qty);
                repo.save(entry);
                count++;
            }
        }
        return count;
    }

    private String getCellString(Cell cell) {
        return ExcelCellUtil.getString(cell);
    }

    private Integer getCellInteger(Cell cell) {
        return ExcelCellUtil.getInteger(cell);
    }
}
