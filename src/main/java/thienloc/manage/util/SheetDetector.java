package thienloc.manage.util;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Locates the daily-production detail sheet in an uploaded workbook.
 *
 * Search order:
 *   1. Sheet named "D"  (factory EFF APR V6 layout)
 *   2. Sheet named "DB" (project's own template)
 *   3. Any sheet whose header row 1 matches the daily-detail signature
 *      (Date + DL + Output + RFT + Article via {@link HeaderResolver})
 *
 * Note: the factory workbook also has a sheet named "DB", but it holds the
 * article master (REF, Article No., Pattern No., …). That sheet does NOT
 * carry a daily-detail signature and is therefore correctly skipped.
 */
public final class SheetDetector {

    private SheetDetector() {}

    public static Sheet findDailyDetailSheet(Workbook wb) {
        if (wb == null) {
            throw new IllegalArgumentException("Workbook is null");
        }

        Sheet d = wb.getSheet("D");
        if (d != null && hasDailySignature(d)) return d;

        Sheet db = wb.getSheet("DB");
        if (db != null && hasDailySignature(db)) return db;

        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet s = wb.getSheetAt(i);
            if (hasDailySignature(s)) return s;
        }

        throw new IllegalStateException(
                "No daily-detail sheet found in workbook. Expected a sheet whose header row "
                        + "contains: Date, DL, Output, RFT, Article.");
    }

    /** Returns true if the sheet's header row 1 carries the daily-detail signature. */
    public static boolean hasDailySignature(Sheet sheet) {
        if (sheet == null) return false;
        HeaderResolver resolver = HeaderResolver.tryResolve(sheet);
        if (resolver == null) return false;
        return resolver.has(CanonicalColumn.DATE)
                && resolver.has(CanonicalColumn.DL)
                && resolver.has(CanonicalColumn.OUTPUT)
                && resolver.has(CanonicalColumn.RFT)
                && resolver.has(CanonicalColumn.ARTICLE);
    }
}
