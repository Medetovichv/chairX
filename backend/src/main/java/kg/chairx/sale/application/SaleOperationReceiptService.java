package kg.chairx.sale.application;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import kg.chairx.sale.api.SaleResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Request-specific sale mutation receipts. The reservation and resulting mutation are
 * atomic: an HTTP response can be lost, but a committed receipt is never ambiguous.
 *
 * Missing receipts are NOT proof of failure while a concurrent request may be in flight.
 */
@Service
public class SaleOperationReceiptService {
    private final JdbcClient jdbc;
    private final JsonMapper mapper;

    public SaleOperationReceiptService(JdbcClient jdbc, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public SaleResponse execute(UUID saleId, UUID operationId, String action,
                                String fingerprint, Supplier<SaleResponse> mutation) {
        if (operationId == null) {
            // Backward-compatible clients continue to use the established endpoint.
            return mutation.get();
        }
        String actor = actor();
        int inserted = jdbc.sql("""
                INSERT INTO sale_operation_receipts
                    (operation_id, sale_id, actor, action, request_fingerprint)
                VALUES (:operationId, :saleId, :actor, :action, :fingerprint)
                ON CONFLICT (operation_id) DO NOTHING
                """)
                .param("operationId", operationId).param("saleId", saleId)
                .param("actor", actor).param("action", action)
                .param("fingerprint", fingerprint).update();
        if (inserted == 0) {
            Receipt previous = jdbc.sql("""
                    SELECT sale_id, actor, action, request_fingerprint, response_json::text AS response_json
                    FROM sale_operation_receipts WHERE operation_id = :operationId
                    """)
                    .param("operationId", operationId)
                    .query((rs, row) -> new Receipt(
                            rs.getObject("sale_id", UUID.class),
                            rs.getString("actor"), rs.getString("action"),
                            rs.getString("request_fingerprint"), rs.getString("response_json")))
                    .single();
            if (!saleId.equals(previous.saleId()) || !actor.equals(previous.actor())
                    || !action.equals(previous.action())
                    || !fingerprint.equals(previous.fingerprint())
                    || previous.responseJson() == null) {
                throw conflict();
            }
            return mapper.readValue(previous.responseJson(), SaleResponse.class);
        }
        SaleResponse result = mutation.get();
        int saved = jdbc.sql("""
                UPDATE sale_operation_receipts
                SET response_json = CAST(:response AS jsonb)
                WHERE operation_id = :operationId AND response_json IS NULL
                """)
                .param("operationId", operationId)
                .param("response", mapper.writeValueAsString(result))
                .update();
        if (saved != 1) throw new IllegalStateException("Sale receipt not persisted");
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<SaleOperationReceiptResult> find(UUID saleId, UUID operationId) {
        String actor = actor();
        return jdbc.sql("""
                SELECT action, response_json::text AS response_json
                FROM sale_operation_receipts
                WHERE operation_id = :operationId AND sale_id = :saleId
                  AND actor = :actor AND response_json IS NOT NULL
                """)
                .param("operationId", operationId).param("saleId", saleId)
                .param("actor", actor)
                .query((rs, row) -> new SaleOperationReceiptResult(
                        operationId, rs.getString("action"), "COMMITTED",
                        mapper.readValue(rs.getString("response_json"), SaleResponse.class)))
                .optional();
    }

    private String actor() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private SaleRuleViolationException conflict() {
        return new SaleRuleViolationException(
                "SALE_OPERATION_KEY_CONFLICT",
                "Ключ операции уже используется для другого запроса");
    }

    private record Receipt(UUID saleId, String actor, String action,
                           String fingerprint, String responseJson) {}

    public record SaleOperationReceiptResult(UUID operationId, String action,
                                             String status, SaleResponse sale) {}
}
