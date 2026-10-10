package kg.chairx.finance.api;

import java.math.BigDecimal;
import java.time.Instant;

public record CashFlowSummaryResponse(
        Instant from,
        Instant to,
        BigDecimal payments,
        BigDecimal paymentCorrections,
        BigDecimal refunds,
        BigDecimal exchangePayments,
        BigDecimal exchangeRefunds,
        BigDecimal operatingExpenses,
        BigDecimal purchasePayments,
        BigDecimal totalIn,
        BigDecimal totalOut,
        BigDecimal netCashFlow
) {
}