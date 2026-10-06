package kg.chairx.sale;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import kg.chairx.sale.api.CreateSaleItemRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SaleMoneyValidationTests {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation
                .buildDefaultValidatorFactory()
                .getValidator();
    }

    @Test
    void wholeSomPriceIsValid() {
        var request = item("8500");

        var violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void zeroPriceIsValid() {
        var request = item("0");

        var violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void oneDecimalPlaceIsRejected() {
        var request = item("8500.1");

        var violations = validator.validate(request);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath()
                                .toString()
                                .equals("unitSalePrice")
                );
    }

    @Test
    void twoDecimalPlacesAreRejected() {
        var request = item("8500.01");

        var violations = validator.validate(request);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath()
                                .toString()
                                .equals("unitSalePrice")
                );
    }

    @Test
    void negativePriceIsRejected() {
        var request = item("-1");

        var violations = validator.validate(request);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath()
                                .toString()
                                .equals("unitSalePrice")
                );
    }

    private CreateSaleItemRequest item(String price) {
        return new CreateSaleItemRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                new BigDecimal(price)
        );
    }
}