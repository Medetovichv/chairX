package kg.chairx.sale.api;

import jakarta.validation.Validation;
import kg.chairx.sale.domain.SaleCancellationReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CancelSaleRequestTest {
    @Test
    void reasonIsMandatoryForNewCancellation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new CancelSaleRequest(null, "comment")).stream()
                    .anyMatch(v -> "reason".equals(v.getPropertyPath().toString())));
            assertTrue(validator.validate(new CancelSaleRequest(
                    SaleCancellationReason.CUSTOMER_CHANGED_MIND, "comment")).isEmpty());
        }
    }

    @Test
    void onlySixApprovedReasonsAreExposed() {
        assertArrayEquals(new String[]{
                "CUSTOMER_CHANGED_MIND",
                "PRODUCT_NOT_SUITABLE",
                "PRICE_NOT_ACCEPTABLE",
                "DUPLICATE_ORDER",
                "DELIVERY_UNAVAILABLE",
                "OTHER"
        }, java.util.Arrays.stream(SaleCancellationReason.values())
                .map(Enum::name).toArray(String[]::new));
    }

    @Test
    void commentCannotExceedOneThousandCharacters() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new CancelSaleRequest(
                    SaleCancellationReason.OTHER, "x".repeat(1001))).stream()
                    .anyMatch(v -> "comment".equals(v.getPropertyPath().toString())));
            assertTrue(validator.validate(new CancelSaleRequest(
                    SaleCancellationReason.OTHER, "x".repeat(1000))).isEmpty());
        }
    }
}
