package kg.chairx.finance.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DailyClosingResponse(UUID id, LocalDate businessDate, Instant createdAt,
        String createdBy, Account cash, Account bank, long version) {
    public DailyClosingResponse(UUID id, LocalDate businessDate, Instant createdAt,
                                String createdBy, Account cash, Account bank) {
        this(id, businessDate, createdAt, createdBy, cash, bank, 0);
    }

    /**
     * expected / difference are the latest journal-based view.
     * originalExpected preserves the expected amount at the initial closing.
     * requiresReview flags new discrepancies produced by a later correction;
     * it is not an assertion of shortage or a fabricated expense.
     */
    public record Account(BigDecimal expected, BigDecimal actual, BigDecimal difference,
                          String note, BigDecimal originalExpected, boolean recalculated,
                          boolean requiresReview) {
        public Account(BigDecimal expected, BigDecimal actual,
                       BigDecimal difference, String note) {
            this(expected, actual, difference, note, expected, false, false);
        }
    }
}
