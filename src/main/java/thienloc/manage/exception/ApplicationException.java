package thienloc.manage.exception;

/**
 * Base class for all application-specific runtime exceptions.
 * Subclass this instead of throwing raw {@link RuntimeException} so that
 * {@link GlobalExceptionHandler} can give meaningful HTTP responses.
 */
public abstract class ApplicationException extends RuntimeException {

    protected ApplicationException(String message) {
        super(message);
    }

    protected ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
