package thienloc.manage.util;

import java.util.List;

/**
 * Canonical column identifiers for the daily-production Excel sheet.
 * Synonyms allow the same logical column to be matched whether the workbook
 * came from the project's template ("Section", "Line", "sub-line") or the
 * factory's "EFF APR V6" production workbook ("Section" labelled differently,
 * REF 1 / REF 2 prefix columns, etc.).
 */
public enum CanonicalColumn {
    REF1(false, "REF 1", "REF1", "Reference 1"),
    REF2(false, "REF 2", "REF2", "Reference 2"),
    DATE(true, "Date", "Production Date", "Ngày"),
    SECTION(true, "Section", "Bộ phận"),
    LINE(true, "Line", "Chuyền"),
    SUBLINE(false, "Subline", "Sub-line", "sub-line", "Sub Line", "Sub-Line"),
    SUBSECTION(false, "Subsection", "Sub-section", "sub-section"),
    DL(true, "DL", "Direct Labor", "Manpower"),
    DLI(true, "DLI"),
    IDL(true, "IDL"),
    OUTPUT(true, "Output", "Total Output", "Sản lượng"),
    WT(true, "WT", "Working Time"),
    RFT(true, "RFT", "Right First Time"),
    ARTICLE(true, "Article", "ARTICLE", "Mã hàng"),
    ALLOWANCE(false, "Allowance");

    private final boolean required;
    private final List<String> synonyms;

    CanonicalColumn(boolean required, String... synonyms) {
        this.required = required;
        this.synonyms = List.of(synonyms);
    }

    public boolean isRequired() {
        return required;
    }

    public List<String> getSynonyms() {
        return synonyms;
    }

    /** Case-insensitive synonym match. */
    public boolean matches(String header) {
        if (header == null) return false;
        String h = header.trim();
        for (String s : synonyms) {
            if (s.equalsIgnoreCase(h)) return true;
        }
        return false;
    }
}
