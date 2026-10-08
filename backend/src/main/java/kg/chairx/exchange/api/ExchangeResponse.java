package kg.chairx.exchange.api;

import kg.chairx.exchange.domain.Exchange;
import kg.chairx.exchange.domain.ExchangeStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ExchangeResponse(
        UUID id,
        UUID originalSaleId,
        UUID newSaleId,
        UUID returnId,
        ExchangeStatus status,
        BigDecimal returnedValue,
        BigDecimal newSaleTotal,
        BigDecimal creditApplied,
        BigDecimal additionalPaymentDue,
        BigDecimal refundDue
) {
    public static ExchangeResponse from(Exchange exchange) {
        return new ExchangeResponse(
                exchange.id(),
                exchange.originalSaleId(),
                exchange.newSaleId(),
                exchange.returnId(),
                exchange.status(),
                exchange.returnedValue(),
                exchange.newSaleTotal(),
                exchange.creditApplied(),
                exchange.additionalPaymentDue(),
                exchange.refundDue()
        );
    }
}