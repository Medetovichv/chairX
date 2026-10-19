package kg.chairx.finance.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/finance/ledger")
public class FinanceLedgerController {
    private static final ZoneId ZONE=ZoneId.of("Asia/Bishkek");
    private final JdbcClient jdbc;
    public FinanceLedgerController(JdbcClient jdbc){this.jdbc=jdbc;}

    public record Line(UUID id, Instant postedAt, LocalDate businessDate,
                       String account, BigDecimal amount, String type,
                       String sourceType, UUID sourceId, String createdBy) {}
    public record Page(List<Line> items,long total,int page,int size) {}

    /**
     * Every cash journal entry, including inter-account transfers, is visible
     * exactly once. Profit and revenue are intentionally NOT inferred here.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public Page list(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                     @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
                     @RequestParam(required=false) String account,
                     @RequestParam(required=false) String movementType,
                     @RequestParam(defaultValue="0") int page,
                     @RequestParam(defaultValue="50") int size){
        if(from==null||to==null||!from.isBefore(to)
                ||java.time.temporal.ChronoUnit.DAYS.between(from,to)>366
                ||page<0||size<1||size>100||page>10000)
            throw new IllegalArgumentException("Invalid ledger pagination or period");
        if(account!=null&&!Set.of("CASH","BANK").contains(account))
            throw new IllegalArgumentException("Invalid finance account");
        if(movementType!=null&&!movementType.matches("[A-Z_]{1,50}"))
            throw new IllegalArgumentException("Invalid movement type");
        Instant start=from.atStartOfDay(ZONE).toInstant();
        Instant end=to.atStartOfDay(ZONE).toInstant();
        String clause="""
                FROM finance_movements
                WHERE created_at>=:from AND created_at<:to
                  AND (:account IS NULL OR account_code=:account)
                  AND (:kind IS NULL OR movement_type=:kind)
                """;
        var base=jdbc.sql("SELECT COUNT(*) "+clause)
                .param("from",start).param("to",end)
                .param("account",account).param("kind",movementType);
        long count=base.query(Long.class).single();
        var entries=jdbc.sql("""
             SELECT id,created_at,business_date,account_code,amount,
                    movement_type,source_type,source_id,created_by
             """+clause+" ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset")
             .param("from",start).param("to",end)
             .param("account",account).param("kind",movementType)
             .param("limit",size).param("offset",page*size)
             .query((rs,row)->new Line(
                 rs.getObject("id",UUID.class),
                 rs.getTimestamp("created_at").toInstant(),
                 rs.getDate("business_date")==null?null:rs.getDate("business_date").toLocalDate(),
                 rs.getString("account_code"),rs.getBigDecimal("amount"),
                 rs.getString("movement_type"),rs.getString("source_type"),
                 rs.getObject("source_id",UUID.class),rs.getString("created_by"))).list();
        return new Page(entries,count,page,size);
    }
}
