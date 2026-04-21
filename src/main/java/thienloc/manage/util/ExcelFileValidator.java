package thienloc.manage.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

public final class ExcelFileValidator {

    // ZIP/PK — used by XLSX (Office Open XML)
    private static final byte[] XLSX_MAGIC = { 0x50, 0x4B, 0x03, 0x04 };
    // OLE2 compound document — used by XLS (BIFF8)
    private static final byte[] XLS_MAGIC  = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0 };

    private ExcelFileValidator() {}

    /**
     * Validates that {@code file} is a genuine Excel file by inspecting its magic bytes.
     * Throws {@link IllegalArgumentException} if the file is not XLSX or XLS.
     * Safe to call before parse — does not consume the MultipartFile stream.
     */
    public static void validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        byte[] header = new byte[8];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.read(header);
        }

        if (read < 4) {
            throw new IllegalArgumentException("File quá nhỏ, không phải file Excel hợp lệ.");
        }

        if (!startsWith(header, XLSX_MAGIC) && !startsWith(header, XLS_MAGIC)) {
            throw new IllegalArgumentException(
                    "File không hợp lệ. Chỉ chấp nhận file Excel (.xlsx, .xls) thực sự.");
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }
}
