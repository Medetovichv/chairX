package kg.chairx.common.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kg.chairx.expense.application.ExpenseNotFoundException;
import kg.chairx.inventory.domain.InsufficientStockException;
import kg.chairx.customer.application.CustomerNotFoundException;
import kg.chairx.customer.application.CustomerRuleViolationException;
import kg.chairx.delivery.application.DeliveryNotFoundException;
import kg.chairx.delivery.application.DeliveryRuleViolationException;
import kg.chairx.inventory.domain.StockQuantityLimitException;
import kg.chairx.payment.application.PaymentNotFoundException;
import kg.chairx.payment.application.PaymentRuleViolationException;
import kg.chairx.product.application.ProductNotFoundException;
import kg.chairx.product.application.ProductVariantNotFoundException;
import kg.chairx.purchase.application.PurchaseNotFoundException;
import kg.chairx.purchase.application.PurchaseRuleViolationException;
import kg.chairx.refund.application.RefundNotFoundException;
import kg.chairx.refund.application.RefundRuleViolationException;
import kg.chairx.returning.application.ReturnNotFoundException;
import kg.chairx.returning.application.ReturnRuleViolationException;
import kg.chairx.sale.application.SaleNotFoundException;
import kg.chairx.sale.application.SaleRuleViolationException;
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
import kg.chairx.exchange.application.ExchangeNotFoundException;
import kg.chairx.exchange.application.ExchangeRuleViolationException;
import kg.chairx.expense.application.ExpenseValidationException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG =
            LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(kg.chairx.inventory.cost.InventoryCostException.class)
    ResponseEntity<ApiError> inventoryCostConflict(kg.chairx.inventory.cost.InventoryCostException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(exception.getCode(), exception.getMessage()));
    }

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

    @ExceptionHandler(ExpenseValidationException.class)
    ResponseEntity<ApiError> expenseValidation(
            ExpenseValidationException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(ApiError.of(
                        "EXPENSE_VALIDATION_ERROR",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(ExpenseNotFoundException.class)
    ResponseEntity<ApiError> expenseNotFound(
            ExpenseNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(
                        "EXPENSE_NOT_FOUND",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(
            kg.chairx.inventory.application.InvalidTransferQueryException.class
    )
    ResponseEntity<ApiError> invalidTransferQuery(
            kg.chairx.inventory.application.InvalidTransferQueryException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(ApiError.of(
                        "INVALID_TRANSFER_QUERY",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(
            kg.chairx.inventory.application.InventoryTransferNotFoundException.class
    )
    ResponseEntity<ApiError> inventoryTransferNotFound(
            kg.chairx.inventory.application.InventoryTransferNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(
                        "INVENTORY_TRANSFER_NOT_FOUND",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(
            kg.chairx.inventory.application.InventoryTransferConflictException.class
    )
    ResponseEntity<ApiError> inventoryTransferConflict(
            kg.chairx.inventory.application.InventoryTransferConflictException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(
                        "TRANSFER_OPERATION_CONFLICT",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(InsufficientStockException.class)
    ResponseEntity<ApiError> insufficientStock(
            InsufficientStockException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(
                        "INSUFFICIENT_STOCK",
                        exception.getMessage()
                ));
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

    @ExceptionHandler(SaleNotFoundException.class)
    ResponseEntity<ApiError> saleNotFound(
            SaleNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ApiError.of(
                                "SALE_NOT_FOUND",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(ExchangeNotFoundException.class)
    ResponseEntity<ApiError> exchangeNotFound(
            ExchangeNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(
                        "EXCHANGE_NOT_FOUND",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(ExchangeRuleViolationException.class)
    ResponseEntity<ApiError> exchangeRuleViolation(
            ExchangeRuleViolationException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(
                        exception.getCode(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(SaleRuleViolationException.class)
    ResponseEntity<ApiError> saleRuleViolation(
            SaleRuleViolationException exception
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

    @ExceptionHandler(PaymentNotFoundException.class)
    ResponseEntity<ApiError> paymentNotFound(
            PaymentNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ApiError.of(
                                "PAYMENT_NOT_FOUND",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(PaymentRuleViolationException.class)
    ResponseEntity<ApiError> paymentRuleViolation(
            PaymentRuleViolationException exception
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

    @ExceptionHandler(RefundNotFoundException.class)
    ResponseEntity<ApiError> refundNotFound(
            RefundNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ApiError.of(
                                "REFUND_NOT_FOUND",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(RefundRuleViolationException.class)
    ResponseEntity<ApiError> refundRuleViolation(
            RefundRuleViolationException exception
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

    @ExceptionHandler(DeliveryNotFoundException.class)
    ResponseEntity<ApiError> deliveryNotFound(
            DeliveryNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ApiError.of(
                                "DELIVERY_NOT_FOUND",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(DeliveryRuleViolationException.class)
    ResponseEntity<ApiError> deliveryRuleViolation(
            DeliveryRuleViolationException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiError.of(
                                exception.code(),
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(ReturnNotFoundException.class)
    ResponseEntity<ApiError> returnNotFound(
            ReturnNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ApiError.of(
                                "RETURN_NOT_FOUND",
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(ReturnRuleViolationException.class)
    ResponseEntity<ApiError> returnRuleViolation(
            ReturnRuleViolationException exception
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

    @ExceptionHandler({kg.chairx.finance.application.FinancePostingException.class,
            kg.chairx.finance.domain.InsufficientFundsException.class,
            kg.chairx.finance.domain.FinanceAccountOperationException.class})
    ResponseEntity<ApiError> financeConflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("FINANCE_OPERATION_CONFLICT", "Финансовая операция отклонена: проверьте остаток, инициализацию счёта и закрытие дня"));
    }

    @ExceptionHandler(kg.chairx.finance.application.FinanceValidationException.class)
    ResponseEntity<ApiError> financeValidation() {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REQUEST", "Некорректные параметры финансовой операции"));
    }

    @ExceptionHandler(kg.chairx.finance.application.FinanceConflictException.class)
    ResponseEntity<ApiError> financeBusinessConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("FINANCE_OPERATION_CONFLICT", "Финансовая операция недоступна в текущем состоянии"));
    }

    @ExceptionHandler(kg.chairx.finance.application.ClosingNotFoundException.class)
    ResponseEntity<ApiError> closingNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("CLOSING_NOT_FOUND", "Закрытие дня не найдено"));
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