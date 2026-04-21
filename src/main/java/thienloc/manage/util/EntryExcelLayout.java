package thienloc.manage.util;

import java.util.Arrays;
import java.util.List;

/**
 * Column layout constants for the daily-production Excel template.
 * Must be kept in sync between the template generator and the importer.
 *
 * Columns: 0=Date 1=Section 2=Line 3=sub-line 4=sub-section 5=DL 6=DLI 7=IDL
 * 8=Output 9=WT 10=RFT 11..25=Article time slots 26=ARTICLE 27=Allowance
 */
public final class EntryExcelLayout {

    private EntryExcelLayout() {}

    public static final int COL_DATE = 0;
    public static final int COL_SECTION = 1;
    public static final int COL_LINE = 2;
    public static final int COL_SUBLINE = 3;
    public static final int COL_SUBSECTION = 4;
    public static final int COL_DL = 5;
    public static final int COL_DLI = 6;
    public static final int COL_IDL = 7;
    public static final int COL_OUTPUT = 8;
    public static final int COL_WT = 9;
    public static final int COL_RFT = 10;
    public static final int COL_SLOTS_START = 11;
    public static final int COL_ARTICLE = 26;
    public static final int COL_ALLOWANCE = 27;
    public static final int DATA_START_ROW = 2;

    public static final List<String> TIME_SLOTS = Arrays.asList(
            "07:00-08:00", "08:00-09:00", "09:00-10:00", "10:00-11:00",
            "11:00-12:00", "12:00-13:00", "13:00-14:00", "14:00-15:00",
            "15:00-16:00", "16:00-17:00", "17:00-18:00", "18:00-19:00",
            "19:00-20:00", "20:00-21:00", "21:00-22:00");
}
