package thienloc.manage.exception;

/**
 * Thrown when an Excel import fails due to a malformed file, missing sheet,
 * or unrecoverable parse error.
 * Maps to HTTP 400 Bad Request.
 */
public class ImportException extends ApplicationException {

    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
