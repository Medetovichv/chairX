package kg.chairx.exchange.application;

import kg.chairx.exchange.domain.ExchangeStatus;
import kg.chairx.exchange.infrastructure.ExchangeRepository;
import kg.chairx.sale.application.SaleRuleViolationException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ExchangeSaleGuard {

    private final ExchangeRepository exchanges;

    public ExchangeSaleGuard(ExchangeRepository exchanges) {
        this.exchanges = exchanges;
    }

    public void requireFulfillmentAllowed(UUID saleId) {
        var exchange = exchanges.lockByNewSale(saleId);

        if (exchange.isPresent()
                && exchange.get().status() != ExchangeStatus.COMPLETED) {
            throw new SaleRuleViolationException(
                    "EXCHANGE_SETTLEMENT_REQUIRED",
                    "Нельзя выдать товар до завершения расчётов по обмену"
            );
        }
    }

    public void requireCancellationAllowed(UUID saleId) {
        if (exchanges.existsByNewSale(saleId)) {
            throw new SaleRuleViolationException(
                    "EXCHANGE_SALE_CANCELLATION_FORBIDDEN",
                    "Продажу, созданную обменом, нельзя отменить обычным способом"
            );
        }
    }
}