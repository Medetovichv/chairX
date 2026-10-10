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

    public record Account(BigDecimal expected, BigDecimal actual, BigDecimal difference, String note) {}
}
