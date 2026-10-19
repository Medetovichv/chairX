package kg.chairx.expense.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure period-based allocation of an already recognized operating cost.
 * This does NOT create payments, finance movements or daily-closing entries.
 * Amounts are whole Kyrgyz som; the remainder is assigned deterministically
 * to the last day of the period to preserve the total.
 */
public final class ExpensePeriodAllocation {
    private ExpensePeriodAllocation() {}

    /**
     * Total accrued within [fromInclusive, toExclusive), including partial
     * overlap with the original allocation period. Does not post payments.
     */
    public static BigDecimal accruedWithin(
            BigDecimal amount, LocalDate periodStart, LocalDate periodEnd,
            LocalDate fromInclusive, LocalDate toExclusive) {
        if (fromInclusive == null || toExclusive == null || fromInclusive.isAfter(toExclusive)) {
            throw new IllegalArgumentException("Invalid reporting interval");
        }
        Map<LocalDate, BigDecimal> daily = allocate(amount, periodStart, periodEnd);
        return daily.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(fromInclusive)
                        && entry.getKey().isBefore(toExclusive))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static Map<LocalDate, BigDecimal> allocate(
            BigDecimal amount, LocalDate fromInclusive, LocalDate toExclusive) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Amount must be a positive whole number of som");
        }
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("Period must have a positive number of days");
        }
        long days = ChronoUnit.DAYS.between(fromInclusive, toExclusive);
        if (days > 366) {
            throw new IllegalArgumentException("Period must not exceed 366 days");
        }
        BigDecimal[] quotient = amount.divideAndRemainder(BigDecimal.valueOf(days));
        LinkedHashMap<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        for (long i = 0; i < days; i++) {
            result.put(fromInclusive.plusDays(i),
                    i == days - 1 ? quotient[0].add(quotient[1]) : quotient[0]);
        }
        return java.util.Collections.unmodifiableMap(result);
    }
}
