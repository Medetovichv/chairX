package kg.chairx.finance.api;

import java.math.BigDecimal;

public record DailyClosingRequest(BigDecimal actualCash, String cashNote,
                                  BigDecimal actualBank, String bankNote) {}
