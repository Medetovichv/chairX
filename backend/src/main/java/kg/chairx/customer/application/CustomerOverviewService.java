package kg.chairx.customer.application;

import kg.chairx.common.web.InvalidQueryException;
import kg.chairx.customer.api.CustomerOverviewPage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CustomerOverviewService {
    private final JdbcClient jdbc;
    public CustomerOverviewService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public CustomerOverviewPage list(String query, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (query != null && query.length() > 200)) {
            throw new InvalidQueryException("Некорректные параметры списка клиентов");
        }
        String match = query == null || query.isBlank() ? null : "%" + query.strip() + "%";
        String condition = """
                WHERE (CAST(:query AS varchar) IS NULL
                       OR c.full_name ILIKE :query OR c.phone ILIKE :query
                       OR c.secondary_phone ILIKE :query
                       OR c.whatsapp_phone ILIKE :query
                       OR c.instagram_username ILIKE :query)
                """;

        var items = jdbc.sql("""
                SELECT c.id, c.full_name,c.phone,c.whatsapp_phone,c.city_region,
                       history.order_count,history.last_order_at,history.last_sale_number
                FROM customers c
                LEFT JOIN LATERAL (
                    SELECT COUNT(*) AS order_count,
                           MAX(s.created_at) AS last_order_at,
                           (array_agg(s.sale_number ORDER BY s.created_at DESC, s.id DESC))[1]
                              AS last_sale_number
                    FROM sales s WHERE s.customer_id = c.id
                ) history ON TRUE
                """+condition+"""
                ORDER BY c.full_name NULLS LAST,c.id
                LIMIT :size OFFSET :offset
                """)
                .param("query",match,Types.VARCHAR)
                .param("size",size).param("offset",(long)page*size)
                .query((rs,row) -> new CustomerOverviewPage.Line(
                        rs.getObject("id",UUID.class),rs.getString("full_name"),
                        rs.getString("phone"),rs.getString("whatsapp_phone"),
                        rs.getString("city_region"),rs.getLong("order_count"),
                        rs.getString("last_sale_number"),
                        rs.getTimestamp("last_order_at") == null ? null :
                                rs.getTimestamp("last_order_at").toInstant())).list();

        long total = jdbc.sql("SELECT count(*) FROM customers c " + condition)
                .param("query",match,Types.VARCHAR).query(Long.class).single();
        return new CustomerOverviewPage(items,page,size,total);
    }
}
