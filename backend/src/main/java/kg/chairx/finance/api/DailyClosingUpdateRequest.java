package kg.chairx.finance.api;

import java.math.BigDecimal;

/** Client revision is mandatory: concurrent stale edits must fail, not overwrite. */
public record DailyClosingUpdateRequest(
        Long expectedVersion,
        BigDecimal actualCash,
        String cashNote,
        BigDecimal actualBank,
        String bankNote,
        String reason
) {}
