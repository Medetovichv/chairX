package kg.chairx.finance.application;

import kg.chairx.finance.api.CashFlowSummaryResponse;
import kg.chairx.finance.infrastructure.CashFlowRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
            throw new IllegalArgumentException(
                    "Период отчёта должен быть корректным: from < to"
            );
        }

        if (!from.atZone(BUSINESS_ZONE).toLocalTime().equals(LocalTime.MIDNIGHT)
                || !to.atZone(BUSINESS_ZONE).toLocalTime().equals(LocalTime.MIDNIGHT)) {
            throw new IllegalArgumentException(
                    "Границы периода должны соответствовать полуночи по Бишкеку"
            );
        }

        // Границы периода в часовом поясе бизнеса
        LocalDate fromDate = from.atZone(BUSINESS_ZONE).toLocalDate();
        LocalDate toDate = to.atZone(BUSINESS_ZONE).toLocalDate();

        // Операционные поступления и выплаты
        BigDecimal payments = repository.payments(from, to);
        BigDecimal refunds = repository.refunds(from, to);

        // Доплаты и возвраты по обменам
        BigDecimal exchangePayments =
                repository.exchangePayments(from, to);

        BigDecimal exchangeRefunds =
                repository.exchangeRefunds(from, to);

        // Расходы бизнеса
        BigDecimal operatingExpenses =
                repository.operatingExpenses(fromDate, toDate);

        // Все поступления
        BigDecimal totalIn = payments.add(exchangePayments);

        // Все выплаты
        BigDecimal totalOut = refunds
                .add(exchangeRefunds)
                .add(operatingExpenses);

        // Итоговый денежный поток
        BigDecimal netCashFlow = totalIn.subtract(totalOut);

        return new CashFlowSummaryResponse(
                from,
                to,
                payments,
                refunds,
                exchangePayments,
                exchangeRefunds,
                operatingExpenses,
                totalIn,
                totalOut,
                netCashFlow
        );
    }
}