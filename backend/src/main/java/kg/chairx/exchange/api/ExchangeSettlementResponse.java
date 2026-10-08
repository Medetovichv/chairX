package kg.chairx.exchange.api;

import kg.chairx.exchange.infrastructure.ExchangeSettlementRepository.Settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExchangeSettlementResponse(
        UUID id,
        UUID exchangeId,
        String direction,
        String method,
        BigDecimal amount,
        String reference,
        UUID idempotencyKey,
        String createdBy,
        Instant createdAt
) {
    public static ExchangeSettlementResponse from(Settlement settlement) {
        return new ExchangeSettlementResponse(
                settlement.id(),
                settlement.exchangeId(),
                settlement.direction(),
                settlement.method(),
                settlement.amount(),
                settlement.reference(),
                settlement.idempotencyKey(),
                settlement.createdBy(),
                settlement.createdAt()
        );
    }
}