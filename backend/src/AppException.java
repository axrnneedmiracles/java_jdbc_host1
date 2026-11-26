/**
 * Custom exception hierarchy covering invalid inputs, file I/O errors, and JDBC database failures.
 */
public class AppException extends Exception {
    private final int statusCode;

    public AppException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public AppException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public static class InvalidInputException extends AppException {
        public InvalidInputException(String message) {
            super(message, 400);
        }
    }

    public static class FileHandlingException extends AppException {
        public FileHandlingException(String message, Throwable cause) {
            super(message, 400, cause);
        }
        public FileHandlingException(String message) {
            super(message, 400);
        }
    }

    public static class DatabaseException extends AppException {
        public DatabaseException(String message, Throwable cause) {
            super(message, 500, cause);
        }
        public DatabaseException(String message, int statusCode) {
            super(message, statusCode);
        }
    }
}
