package thienloc.manage.dto.response;

import java.util.List;

public record ErrorDto(String code, String message, String correlationId, List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {}

    public static ErrorDto of(String code, String message) {
        return new ErrorDto(code, message, null, null);
    }

    public static ErrorDto of(String code, String message, List<FieldError> fieldErrors) {
        return new ErrorDto(code, message, null, fieldErrors);
    }
}
