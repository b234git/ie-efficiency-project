package thienloc.manage.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import thienloc.manage.dto.response.ErrorDto;
import thienloc.manage.dto.response.ErrorDto.FieldError;

import jakarta.validation.ConstraintViolationException;

import java.util.List;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "thienloc.manage.controller.api")
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorDto> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorDto.of("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler({InvalidInputException.class, ImportException.class})
    public ResponseEntity<ErrorDto> handleBadRequest(ApplicationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorDto.of("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorDto.of("VALIDATION_FAILED", "Validation failed.", fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDto> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorDto.of("VALIDATION_FAILED", "Validation failed.", fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDto> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorDto.of("MALFORMED_REQUEST", "Request body is malformed or missing."));
    }

    @ExceptionHandler(DuplicateRecordException.class)
    public ResponseEntity<ErrorDto> handleConflict(DuplicateRecordException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorDto.of("DUPLICATE", ex.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorDto> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorDto.of("CONCURRENT_MODIFICATION",
                        "Bản ghi này vừa được người khác chỉnh sửa. Vui lòng tải lại trang và thử lại."));
    }

    @ExceptionHandler({ServiceUnavailableException.class, CallNotPermittedException.class})
    public ResponseEntity<ErrorDto> handleServiceUnavailable(Exception ex) {
        log.warn("Service unavailable: {}", ex.getMessage());
        String message = (ex instanceof ServiceUnavailableException)
                ? ex.getMessage()
                : "Hệ thống đang bận, vui lòng thử lại sau.";
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorDto.of("SERVICE_UNAVAILABLE", message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDto> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorDto.of("FORBIDDEN", "Access denied."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorDto> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorDto.of("UNAUTHORIZED", "Authentication required."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleGeneral(Exception ex) {
        log.error("Unhandled exception in API: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorDto.of("INTERNAL_ERROR",
                        "An unexpected error occurred. Please try again or contact the administrator."));
    }
}
