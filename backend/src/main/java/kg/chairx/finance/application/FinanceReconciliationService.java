package kg.chairx.finance.application;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Read-only operator diagnosis; intentionally no public endpoint until
 * the P17 permission model has an approved reconciliation permission.
 * Never creates journal entries or modifies historic documents.
 */
@Service
public class FinanceReconciliationService {
    private final JdbcClient jdbc;

    public FinanceReconciliationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Issue> listIssues() {
        return jdbc.sql("""
                SELECT document_source, document_id, account_code,
                       movement_type, expected_amount, posted_amount, problem
                FROM finance_document_posting_issues
                ORDER BY document_source, document_id
                """)
                .query((rs, row) -> new Issue(
                        rs.getString("document_source"),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("account_code"),
                        rs.getString("movement_type"),
                        rs.getBigDecimal("expected_amount"),
                        rs.getBigDecimal("posted_amount"),
                        rs.getString("problem")
                )).list();
    }

    public record Issue(
            String documentSource,
            UUID documentId,
            String account,
            String movementType,
            BigDecimal expectedAmount,
            BigDecimal postedAmount,
            String problem
    ) {}
}
