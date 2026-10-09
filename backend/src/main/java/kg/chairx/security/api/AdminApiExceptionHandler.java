package kg.chairx.security.api;

import kg.chairx.common.web.ApiError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AdminUserController.class)
public class AdminApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalidArgument(IllegalArgumentException ex) {
        String message = ex.getMessage() == null ? "Некорректные данные" : ex.getMessage();
        if (message.contains("не найден") || message.contains("не найдена")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiError.of("NOT_FOUND", message));
        }
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_ARGUMENT", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_JSON", "Некорректный JSON"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> duplicate(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("DATA_CONFLICT", "Данные уже существуют или нарушают ограничения"));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> businessConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("BUSINESS_CONFLICT", ex.getMessage()));
    }
}
