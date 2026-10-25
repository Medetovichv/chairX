package kg.chairx.exchange;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.exchange.api.CreateExchangeRequest;
import kg.chairx.exchange.application.ExchangeRuleViolationException;
import kg.chairx.exchange.application.ExchangeService;
import kg.chairx.sale.domain.FulfillmentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Production-default protection: a new Exchange may never become a replacement
 * for Return -> Refund -> independent Sale.
 */
@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class ExchangeRetirementTests {
    @Autowired ExchangeService exchanges;

    @Test
    void newExchangeIsRejectedBeforeAnyReturnOrSaleIsTouched() {
        var request = new CreateExchangeRequest(
                UUID.randomUUID(), UUID.randomUUID(), FulfillmentType.SELF_PICKUP, List.of());
        assertThatThrownBy(() -> exchanges.create(request))
                .isInstanceOfSatisfying(ExchangeRuleViolationException.class, error ->
                        org.assertj.core.api.Assertions.assertThat(error.getCode())
                                .isEqualTo("EXCHANGE_CREATION_RETIRED"));
    }
}
