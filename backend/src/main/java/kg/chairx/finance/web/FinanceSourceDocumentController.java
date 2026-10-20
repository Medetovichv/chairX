package kg.chairx.finance.web;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Read-only primary financial documents. Never guesses links from a movement UUID. */
@RestController
@RequestMapping("/api/finance/source-documents")
public class FinanceSourceDocumentController {
    private final JdbcClient jdbc;
    public FinanceSourceDocumentController(JdbcClient jdbc) { this.jdbc=jdbc; }

    public record SourceDocument(String kind, UUID id, String title, Map<String,String> fields) {}

    @GetMapping("/{kind}/{id}")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public SourceDocument get(@PathVariable String kind, @PathVariable UUID id, Authentication auth) {
        if ("EXPENSE".equals(kind) && auth.getAuthorities().stream()
                .noneMatch(a -> "EXPENSES_READ".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Нет права читать расходы");
        }
        if ("REFUND".equals(kind) && auth.getAuthorities().stream()
                .noneMatch(a -> "REFUNDS_READ".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Нет права читать возвраты денег");
        }
        SourceDocument doc=switch (kind) {
            case "REFUND" -> jdbc.sql("""
                SELECT amount,method,reason,reference,comment,refunded_by,refunded_at
                FROM refunds WHERE id=:id
                """).param("id",id).query((rs,n)->new SourceDocument(kind,id,"Возврат денег",
                    values("Сумма, KGS",rs.getBigDecimal("amount").toPlainString(),
                           "Способ",rs.getString("method"),
                           "Причина",rs.getString("reason"),
                           "Ссылка",rs.getString("reference"),
                           "Комментарий",rs.getString("comment"),
                           "Сотрудник",rs.getString("refunded_by"),
                           "Создан",String.valueOf(rs.getTimestamp("refunded_at"))))).optional().orElse(null);
            case "EXPENSE" -> jdbc.sql("""
                SELECT category, amount, payment_method, expense_date, comment, created_by
                FROM expenses WHERE id=:id
                """).param("id",id).query((rs,n)->new SourceDocument(kind,id,"Расход",
                    values("Категория",rs.getString("category"),
                           "Сумма, KGS",rs.getBigDecimal("amount").toPlainString(),
                           "Счёт",rs.getString("payment_method"),
                           "Дата",String.valueOf(rs.getDate("expense_date")),
                           "Комментарий",rs.getString("comment"),
                           "Сотрудник",rs.getString("created_by")))).optional().orElse(null);
            case "TRANSFER" -> jdbc.sql("""
                SELECT from_account,to_account,amount,created_by,created_at
                FROM finance_transfers WHERE id=:id
                """).param("id",id).query((rs,n)->new SourceDocument(kind,id,"Перевод между счетами",
                    values("Со счёта",rs.getString("from_account"),
                           "На счёт",rs.getString("to_account"),
                           "Сумма, KGS",rs.getBigDecimal("amount").toPlainString(),
                           "Сотрудник",rs.getString("created_by"),
                           "Создан",String.valueOf(rs.getTimestamp("created_at"))))).optional().orElse(null);
            case "ADMIN_ADJUSTMENT" -> jdbc.sql("""
                SELECT account_code,previous_balance,new_balance,delta,reason,created_by
                FROM finance_admin_adjustments WHERE id=:id
                """).param("id",id).query((rs,n)->new SourceDocument(kind,id,"Корректировка остатка",
                    values("Счёт",rs.getString("account_code"),
                           "До, KGS",rs.getBigDecimal("previous_balance").toPlainString(),
                           "После, KGS",rs.getBigDecimal("new_balance").toPlainString(),
                           "Изменение, KGS",rs.getBigDecimal("delta").toPlainString(),
                           "Причина",rs.getString("reason"),
                           "Сотрудник",rs.getString("created_by")))).optional().orElse(null);
            case "OPENING_BALANCE" -> jdbc.sql("""
                SELECT account_code,amount,created_at,created_by
                FROM finance_movements
                WHERE source_type='OPENING_BALANCE' AND source_id=:id
                ORDER BY created_at,id LIMIT 1
                """).param("id",id).query((rs,n)->new SourceDocument(kind,id,"Начальный остаток",
                    values("Счёт",rs.getString("account_code"),
                           "Сумма, KGS",rs.getBigDecimal("amount").toPlainString(),
                           "Создан",String.valueOf(rs.getTimestamp("created_at")),
                           "Сотрудник",rs.getString("created_by")))).optional().orElse(null);
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Unsupported source type");
        };
        if(doc==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Document not found");
        return doc;
    }

    private static Map<String,String> values(String... pairs) {
        var map=new LinkedHashMap<String,String>();
        for(int i=0;i<pairs.length;i+=2) if(pairs[i+1]!=null) map.put(pairs[i],pairs[i+1]);
        return map;
    }
}
