package thienloc.manage.exception;

/**
 * Thrown when input data fails domain validation
 * (e.g. missing required field, value out of range).
 * Maps to HTTP 400 Bad Request.
 */
public class InvalidInputException extends ApplicationException {

    public InvalidInputException(String message) {
        super(message);
    }
}
