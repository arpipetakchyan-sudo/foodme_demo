package am.foodme.backend.exceptionHandler;

/** The caller is authenticated but not allowed to act on this resource (HTTP 403). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
