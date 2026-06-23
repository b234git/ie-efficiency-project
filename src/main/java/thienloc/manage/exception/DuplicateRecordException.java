package thienloc.manage.exception;

/**
 * Thrown when an operation would create a duplicate record that violates a
 * uniqueness constraint (e.g. duplicate date/section/line combination).
 * Maps to HTTP 409 Conflict.
 */
public class DuplicateRecordException extends ApplicationException {

    /** Id of the already-existing record that this operation collided with (may be null). */
    private final Long existingId;

    public DuplicateRecordException(String message) {
        this(message, null);
    }

    public DuplicateRecordException(String message, Long existingId) {
        super(message);
        this.existingId = existingId;
    }

    public Long getExistingId() {
        return existingId;
    }
}
