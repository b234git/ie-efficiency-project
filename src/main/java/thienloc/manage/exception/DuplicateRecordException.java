package thienloc.manage.exception;

/**
 * Thrown when an operation would create a duplicate record that violates a
 * uniqueness constraint (e.g. duplicate date/section/line combination).
 * Maps to HTTP 409 Conflict.
 */
public class DuplicateRecordException extends ApplicationException {

    public DuplicateRecordException(String message) {
        super(message);
    }
}
