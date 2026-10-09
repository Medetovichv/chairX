package kg.chairx.finance.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DailyClosingResponse(UUID id, LocalDate businessDate, Instant createdAt,
        String createdBy, Account cash, Account bank) {
    public record Account(BigDecimal expected, BigDecimal actual, BigDecimal difference, String note) {}
}
