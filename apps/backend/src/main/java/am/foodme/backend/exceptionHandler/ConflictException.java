package am.foodme.backend.exceptionHandler;

/** The request is valid, but the resource's current state doesn't allow it (HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
