package thienloc.manage.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

/**
 * A minimal {@link MultipartFile} backed by an in-memory byte array, so an uploaded
 * file held in the HTTP session (for the import preview→confirm flow) can be replayed
 * through the existing {@code importXxxFromExcel(MultipartFile)} methods at commit time
 * without re-uploading. Serializable so it can live in the session.
 */
public class BytesMultipartFile implements MultipartFile, Serializable {

    private final String name;
    private final String originalFilename;
    private final byte[] content;

    public BytesMultipartFile(String name, String originalFilename, byte[] content) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.content = content != null ? content : new byte[0];
    }

    @Override public String getName() { return name; }
    @Override public String getOriginalFilename() { return originalFilename; }
    @Override public String getContentType() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }
    @Override public boolean isEmpty() { return content.length == 0; }
    @Override public long getSize() { return content.length; }
    @Override public byte[] getBytes() { return content; }
    @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
    @Override public void transferTo(java.io.File dest) throws IOException {
        java.nio.file.Files.write(dest.toPath(), content);
    }
}
