package kg.chairx.sale.application;

import java.util.Map;
import java.util.UUID;
import kg.chairx.audit.AuditService;
import kg.chairx.sale.api.CreateSaleCommentRequest;
import kg.chairx.sale.api.SaleCommentResponse;
import kg.chairx.sale.api.SaleCommentsPage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only employee comments. Deliberately separate from sale.comment,
 * and from raw audit entry payloads. No inventory/finance side effects.
 */
@Service
public class SaleCommentService {
    private final JdbcClient jdbc;
    private final SaleService sales;
    private final AuditService audit;

    public SaleCommentService(JdbcClient jdbc, SaleService sales, AuditService audit) {
        this.jdbc = jdbc;
        this.sales = sales;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public SaleCommentsPage list(UUID saleId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new kg.chairx.common.web.InvalidQueryException("Некорректные параметры комментариев");
        }
        sales.get(saleId);
        long total = jdbc.sql("SELECT count(*) FROM sale_comments WHERE sale_id=:sale")
                .param("sale", saleId).query(Long.class).single();
        var items = jdbc.sql("""
                SELECT id,sale_id,author,text,created_at FROM sale_comments
                WHERE sale_id=:sale
                ORDER BY created_at DESC,id DESC
                LIMIT :size OFFSET :offset
                """)
                .param("sale", saleId).param("size", size)
                .param("offset", (long) page * size)
                .query((rs,row) -> new SaleCommentResponse(
                        rs.getObject("id",UUID.class),rs.getObject("sale_id",UUID.class),
                        rs.getString("author"),rs.getString("text"),
                        rs.getTimestamp("created_at").toInstant())).list();
        return new SaleCommentsPage(items,page,size,total);
    }

    @Transactional
    public SaleCommentResponse create(UUID saleId, CreateSaleCommentRequest request) {
        sales.get(saleId);
        String body = request.text() == null ? "" : request.text().strip();
        if (body.isBlank() || body.length() > 2000) {
            throw new SaleRuleViolationException("INVALID_COMMENT","Комментарий должен содержать от 1 до 2000 символов");
        }
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        UUID commentId = UUID.randomUUID();
        int inserted = jdbc.sql("""
                INSERT INTO sale_comments(id,sale_id,idempotency_key,author,text,created_at)
                VALUES (:id,:sale,:key,:actor,:body,clock_timestamp())
                ON CONFLICT (idempotency_key) DO NOTHING
                """)
                .param("id",commentId).param("sale",saleId)
                .param("key",request.idempotencyKey()).param("actor",actor)
                .param("body",body).update();

        var saved = jdbc.sql("""
                SELECT id,sale_id,author,text,created_at FROM sale_comments
                WHERE idempotency_key=:key
                """).param("key",request.idempotencyKey())
                .query((rs,row) -> new SaleCommentResponse(
                        rs.getObject("id",UUID.class),rs.getObject("sale_id",UUID.class),
                        rs.getString("author"),rs.getString("text"),
                        rs.getTimestamp("created_at").toInstant()))
                .single();
        if (!saved.saleId().equals(saleId) || !saved.author().equals(actor) ||
                !saved.text().equals(body)) {
            throw new SaleRuleViolationException("COMMENT_IDEMPOTENCY_CONFLICT",
                    "Этот ключ уже использован для другого комментария");
        }
        if (inserted==1) {
            // Audit the action, not the private message body.
            audit.record("SALE_COMMENT",saved.id(),"CREATED",null,
                    Map.of("saleId",saleId.toString(),"commentId",saved.id().toString()));
        }
        return saved;
    }
}
