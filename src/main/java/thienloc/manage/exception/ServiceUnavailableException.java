package thienloc.manage.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ServiceUnavailableException extends ApplicationException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
