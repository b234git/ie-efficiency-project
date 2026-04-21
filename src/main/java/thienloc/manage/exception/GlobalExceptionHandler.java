package thienloc.manage.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView handleNotFound(ResourceNotFoundException ex) {
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "404");
        mav.addObject("message", ex.getMessage());
        return mav;
    }

    @ExceptionHandler({InvalidInputException.class, ImportException.class})
    public ModelAndView handleBadRequest(ApplicationException ex) {
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "400");
        mav.addObject("message", ex.getMessage());
        return mav;
    }

    @ExceptionHandler(DuplicateRecordException.class)
    public ModelAndView handleConflict(DuplicateRecordException ex) {
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "409");
        mav.addObject("message", ex.getMessage());
        return mav;
    }

    @ExceptionHandler({ServiceUnavailableException.class, CallNotPermittedException.class})
    public ModelAndView handleServiceUnavailable(Exception ex) {
        log.warn("Service unavailable: {}", ex.getMessage());
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "503");
        mav.addObject("message", ex instanceof ServiceUnavailableException
                ? ex.getMessage()
                : "Hệ thống đang bận, vui lòng thử lại sau.");
        return mav;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ModelAndView handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "409");
        mav.addObject("message", "Bản ghi này vừa được người khác chỉnh sửa. Vui lòng tải lại trang và thử lại.");
        return mav;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNoResource(HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleGeneral(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", "500");
        mav.addObject("message", "An unexpected error occurred. Please try again or contact the administrator.");
        return mav;
    }
}
