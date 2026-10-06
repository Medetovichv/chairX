package kg.chairx.common.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import kg.chairx.customer.application.CustomerNotFoundException;
import kg.chairx.customer.application.CustomerRuleViolationException;
import kg.chairx.inventory.domain.StockQuantityLimitException;
import kg.chairx.product.application.ProductNotFoundException;
import kg.chairx.product.application.ProductVariantNotFoundException;
import kg.chairx.purchase.application.PurchaseNotFoundException;
import kg.chairx.purchase.application.PurchaseRuleViolationException;
import kg.chairx.supplier.application.SupplierNotFoundException;
import kg.chairx.warehouse.application.WarehouseCodeAlreadyExistsException;
import kg.chairx.warehouse.application.WarehouseNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG =
            LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(CustomerNotFoundException.class)
    ResponseEntity<ApiError> customerNotFound(
            CustomerNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ApiError.of(
                                "CUSTOMER_NOT_FOUND",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(CustomerRuleViolationException.class)
    ResponseEntity<ApiError> customerRuleViolation(
            CustomerRuleViolationException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiError.of(
                                "CUSTOMER_RULE_VIOLATION",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(SupplierNotFoundException.class)
    ResponseEntity<ApiError> supplierNotFound(
            SupplierNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ApiError.of(
                                "SUPPLIER_NOT_FOUND",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(PurchaseNotFoundException.class)
    ResponseEntity<ApiError> purchaseNotFound(
            PurchaseNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ApiError.of(
                                "PURCHASE_NOT_FOUND",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(PurchaseRuleViolationException.class)
    ResponseEntity<ApiError> purchaseRuleViolation(
            PurchaseRuleViolationException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiError.of(
                                exception.getCode(),
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(StockQuantityLimitException.class)
    ResponseEntity<ApiError> stockLimit(
            StockQuantityLimitException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiError.of(
                                "STOCK_QUANTITY_LIMIT",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(WarehouseNotFoundException.class)
    ResponseEntity<ApiError> warehouseNotFound(
            WarehouseNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ApiError.of(
                                "WAREHOUSE_NOT_FOUND",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(WarehouseCodeAlreadyExistsException.class)
    ResponseEntity<ApiError> warehouseCodeConflict(
            WarehouseCodeAlreadyExistsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiError.of(
                                "WAREHOUSE_CODE_ALREADY_EXISTS",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<ApiError> productNotFound(
            ProductNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ApiError.of(
                                "PRODUCT_NOT_FOUND",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(ProductVariantNotFoundException.class)
    ResponseEntity<ApiError> variantNotFound(
            ProductVariantNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ApiError.of(
                                "PRODUCT_VARIANT_NOT_FOUND",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> concurrentUpdate() {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiError.of(
                                "CONCURRENT_UPDATE",
                                "Данные изменены другим запросом. "
                                        + "Обновите страницу и повторите действие"
                        )
                );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrityViolation() {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiError.of(
                                "DATA_CONFLICT",
                                "Операция нарушает ограничения данных"
                        )
                );
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        Map<String, List<String>> fields =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        error -> error.getField(),
                                        LinkedHashMap::new,
                                        Collectors.mapping(
                                                error -> error.getDefaultMessage(),
                                                Collectors.toList()
                                        )
                                )
                        );

        return new ResponseEntity<>(
                new ApiError(
                        "VALIDATION_ERROR",
                        "Проверьте заполнение полей",
                        Map.of("fields", fields)
                ),
                headers,
                status
        );
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String code;
        String message;

        switch (status.value()) {
            case 400 -> {
                code = "INVALID_REQUEST";
                message = "Некорректные параметры или тело запроса";
            }
            case 404 -> {
                code = "NOT_FOUND";
                message = "Ресурс не найден";
            }
            case 405 -> {
                code = "METHOD_NOT_ALLOWED";
                message = "Метод запроса не поддерживается";
            }
            case 406 -> {
                code = "NOT_ACCEPTABLE";
                message = "Запрошенный формат ответа не поддерживается";
            }
            case 415 -> {
                code = "UNSUPPORTED_MEDIA_TYPE";
                message = "Формат тела запроса не поддерживается";
            }
            default -> {
                code = "REQUEST_ERROR";
                message = "Не удалось обработать запрос";
            }
        }

        return new ResponseEntity<>(
                ApiError.of(code, message),
                headers,
                status
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(
            Exception exception
    ) {
        LOG.error(
                "Unexpected API failure",
                exception
        );

        return ResponseEntity
                .internalServerError()
                .body(
                        ApiError.of(
                                "INTERNAL_ERROR",
                                "Внутренняя ошибка сервера"
                        )
                );
    }
}