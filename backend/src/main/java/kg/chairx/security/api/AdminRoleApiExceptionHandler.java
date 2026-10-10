package kg.chairx.security.api;

import kg.chairx.common.web.ApiError;
import kg.chairx.security.application.RoleManagementService.RoleVersionConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = AdminRoleController.class)
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class AdminRoleApiExceptionHandler {
    @ExceptionHandler(RoleVersionConflictException.class)
    ResponseEntity<ApiError> outdated(RoleVersionConflictException e) {
        return ResponseEntity.status(409).body(ApiError.of("VERSION_CONFLICT", e.getMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badInput(IllegalArgumentException e) {
        boolean absent = e.getMessage() != null && e.getMessage().contains("не найдена");
        return ResponseEntity.status(absent ? 404 : 400)
                .body(ApiError.of(absent ? "NOT_FOUND" : "INVALID_ARGUMENT", e.getMessage()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> duplicate(DataIntegrityViolationException e) {
        return ResponseEntity.status(409)
                .body(ApiError.of("DATA_CONFLICT", "Роль уже существует либо нарушено ограничение"));
    }
}
