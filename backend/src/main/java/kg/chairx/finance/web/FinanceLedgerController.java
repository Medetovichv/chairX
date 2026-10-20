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
                       String sourceType, UUID sourceId, String createdBy,
                       String documentKind, UUID documentId, String documentLabel) {}
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
                     @RequestParam(required=false) String search,
                     @RequestParam(required=false) BigDecimal minAmount,
                     @RequestParam(required=false) BigDecimal maxAmount,
                     @RequestParam(defaultValue="newest") String sort,
                     @RequestParam(defaultValue="0") int page,
                     @RequestParam(defaultValue="50") int size){
        if(from==null||to==null||!from.isBefore(to)
                ||java.time.temporal.ChronoUnit.DAYS.between(from,to)>366
                ||page<0||size<1||size>100||page>10000
                ||(search!=null&&search.length()>100)
                ||(minAmount!=null&&maxAmount!=null&&minAmount.compareTo(maxAmount)>0)
                ||!Set.of("newest","oldest","amountAsc","amountDesc").contains(sort))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid ledger pagination or period");
        if(account!=null&&!Set.of("CASH","BANK").contains(account))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid finance account");
        if(movementType!=null&&!movementType.matches("[A-Z_]{1,50}"))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid movement type");
        Instant start=from.atStartOfDay(ZONE).toInstant();
        Instant end=to.atStartOfDay(ZONE).toInstant();
        String clause="""
                FROM finance_movements fm
                LEFT JOIN payments pay ON fm.source_type = 'PAYMENT' AND pay.id=fm.source_id
                LEFT JOIN sales sale ON sale.id=pay.sale_id
                LEFT JOIN purchase_payments pp ON fm.source_type = 'PURCHASE_PAYMENT' AND pp.id=fm.source_id
                LEFT JOIN purchases pur ON pur.id=pp.purchase_id
                LEFT JOIN suppliers supplier ON supplier.id=pur.supplier_id
                WHERE fm.created_at>=:from AND fm.created_at<:to
                  AND (:account = '' OR fm.account_code=:account)
                  AND (:kind = '' OR fm.movement_type=:kind)
                  AND (:search = '' OR fm.created_by ILIKE :searchPattern
                       OR fm.movement_type ILIKE :searchPattern
                       OR fm.source_type ILIKE :searchPattern
                       OR CAST(fm.source_id AS text) ILIKE :searchPattern
                       OR sale.sale_number ILIKE :searchPattern
                       OR supplier.name ILIKE :searchPattern)
                  AND (:minAmount IS NULL OR fm.amount >= :minAmount)
                  AND (:maxAmount IS NULL OR fm.amount <= :maxAmount)
                """;
        String order=switch(sort){case "oldest"->"fm.created_at ASC,fm.id ASC";case "amountAsc"->"fm.amount ASC,fm.id ASC";case "amountDesc"->"fm.amount DESC,fm.id DESC";default->"fm.created_at DESC,fm.id DESC";};
        String q=search==null?"":search.strip();
        String pattern="%"+q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        var base=jdbc.sql("SELECT COUNT(*) "+clause)
                .param("from",java.sql.Timestamp.from(start)).param("to",java.sql.Timestamp.from(end))
                .param("account",account==null?"":account).param("kind",movementType==null?"":movementType)
                .param("search",q).param("searchPattern",pattern)
                .param("minAmount",minAmount,java.sql.Types.NUMERIC).param("maxAmount",maxAmount,java.sql.Types.NUMERIC);
        long count=base.query(Long.class).single();
        var entries=jdbc.sql("""
             SELECT fm.id,fm.created_at,fm.business_date,fm.account_code,fm.amount,
                    fm.movement_type,fm.source_type,fm.source_id,fm.created_by,
                    CASE WHEN sale.id IS NOT NULL THEN 'SALE' WHEN pur.id IS NOT NULL THEN 'PURCHASE' ELSE NULL END AS document_kind,
                    COALESCE(sale.id,pur.id) AS document_id,
                    CASE WHEN sale.id IS NOT NULL THEN sale.sale_number WHEN pur.id IS NOT NULL THEN supplier.name ELSE NULL END AS document_label
             """+clause+" ORDER BY "+order+" LIMIT :limit OFFSET :offset")
             .param("from",java.sql.Timestamp.from(start)).param("to",java.sql.Timestamp.from(end))
             .param("account",account==null?"":account).param("kind",movementType==null?"":movementType)
             .param("search",q).param("searchPattern",pattern)
             .param("minAmount",minAmount,java.sql.Types.NUMERIC).param("maxAmount",maxAmount,java.sql.Types.NUMERIC)
             .param("limit",size).param("offset",page*size)
             .query((rs,row)->new Line(
                 rs.getObject("id",UUID.class),
                 rs.getTimestamp("created_at").toInstant(),
                 rs.getDate("business_date")==null?null:rs.getDate("business_date").toLocalDate(),
                 rs.getString("account_code"),rs.getBigDecimal("amount"),
                 rs.getString("movement_type"),rs.getString("source_type"),
                 rs.getObject("source_id",UUID.class),rs.getString("created_by"),
                 rs.getString("document_kind"),rs.getObject("document_id",UUID.class),
                 rs.getString("document_label"))).list();
        return new Page(entries,count,page,size);
    }

    /** Excel-friendly CSV of ALL rows matching server-side filters.
     * Bounded export: never silently truncate a financial journal.
     */
    @GetMapping(value="/export",produces="text/csv;charset=UTF-8")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public org.springframework.http.ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) String account,
            @RequestParam(required=false) String movementType,
            @RequestParam(required=false) String search,
            @RequestParam(required=false) BigDecimal minAmount,
            @RequestParam(required=false) BigDecimal maxAmount,
            @RequestParam(defaultValue="newest") String sort){
        Page first=list(from,to,account,movementType,search,minAmount,maxAmount,sort,0,100);
        if(first.total()>10000)
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                "Export exceeds 10000 rows; select a shorter period");
        StringBuilder csv=new StringBuilder("\uFEFF")
                .append("Дата;Бизнес-дата;Счёт;Тип;Источник;Сумма;Сотрудник\r\n");
        int pages=(int)((first.total()+99)/100);
        for(int p=0;p<pages;p++){
            List<Line> items=p==0?first.items():list(from,to,account,movementType,search,minAmount,maxAmount,sort,p,100).items();
            for(Line line:items){
                csv.append(csv(line.postedAt().atZone(ZONE).toLocalDateTime().toString())).append(';')
                   .append(csv(line.businessDate()==null?"":line.businessDate().toString())).append(';')
                   .append(csv(line.account())).append(';')
                   .append(csv(line.type())).append(';')
                   .append(csv(line.sourceType())).append(';')
                   .append(line.amount().toPlainString()).append(';')
                   .append(csv(line.createdBy())).append("\r\n");
            }
        }
        byte[] bytes=csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=chairx-finance-"+from+"-"+to+".csv")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }
    private static String csv(String raw){
        String value=raw==null?"":raw;
        // Excel will evaluate formulas even in quoted CSV fields.
        if(!value.isEmpty() && "=+-@".indexOf(value.charAt(0))>=0)
            value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
}
