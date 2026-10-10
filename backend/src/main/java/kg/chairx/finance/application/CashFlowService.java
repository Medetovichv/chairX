package kg.chairx.finance.application;

import kg.chairx.finance.api.CashFlowSummaryResponse;
import kg.chairx.finance.infrastructure.CashFlowRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

@Service
public class CashFlowService {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Bishkek");

    private final CashFlowRepository repository;

    public CashFlowService(CashFlowRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CashFlowSummaryResponse summary(Instant from, Instant to) {

        if (from == null || to == null || !from.isBefore(to)) {
            throw new FinanceValidationException(
                    "Период отчёта должен быть корректным: from < to"
            );
        }

        if (!from.atZone(BUSINESS_ZONE).toLocalTime().equals(LocalTime.MIDNIGHT)
                || !to.atZone(BUSINESS_ZONE).toLocalTime().equals(LocalTime.MIDNIGHT)) {
            throw new FinanceValidationException(
                    "Границы периода должны соответствовать полуночи по Бишкеку"
            );
        }

        BigDecimal payments = repository.payments(from, to);
        BigDecimal corrections = repository.paymentCorrections(from, to);
        BigDecimal refunds = repository.refunds(from, to);

        BigDecimal exchangePayments =
                repository.exchangePayments(from, to);

        BigDecimal exchangeRefunds =
                repository.exchangeRefunds(from, to);

        BigDecimal expenses =
                repository.operatingExpenses(from, to);

        BigDecimal purchasePayments =
                repository.purchasePayments(from, to);

        BigDecimal totalIn = payments.add(exchangePayments);

        // Payment reversals are actual withdrawals, not just accounting
        // corrections. Count them in gross outflow exactly once.
        BigDecimal totalOut = refunds
                .add(exchangeRefunds)
                .add(expenses)
                .add(purchasePayments)
                .add(corrections);

        BigDecimal netCashFlow = totalIn.subtract(totalOut);

        return new CashFlowSummaryResponse(
                from,
                to,
                payments,
                corrections,
                refunds,
                exchangePayments,
                exchangeRefunds,
                expenses,
                purchasePayments,
                totalIn,
                totalOut,
                netCashFlow
        );
    }
}