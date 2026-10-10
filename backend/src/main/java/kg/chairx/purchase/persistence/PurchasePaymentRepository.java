package kg.chairx.purchase.persistence;

import kg.chairx.purchase.domain.PurchasePayment;
import kg.chairx.purchase.domain.PurchasePaymentKind;
import kg.chairx.finance.domain.FinanceAccount;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class PurchasePaymentRepository {
    private final JdbcClient jdbc;

    public PurchasePaymentRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<PurchasePayment> findByKey(UUID key) {
        return jdbc.sql("SELECT * FROM purchase_payments WHERE idempotency_key=:key")
                .param("key",key).query(PurchasePaymentRepository::map).optional();
    }

    public List<PurchasePayment> findByPurchase(UUID purchaseId) {
        return jdbc.sql("""
                SELECT * FROM purchase_payments WHERE purchase_id=:purchase
                ORDER BY created_at,id
                """).param("purchase",purchaseId)
                .query(PurchasePaymentRepository::map).list();
    }

    public BigDecimal total(UUID purchaseId, PurchasePaymentKind kind) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(amount),0) FROM purchase_payments
                WHERE purchase_id=:purchase AND payment_kind=:kind
                """).param("purchase",purchaseId).param("kind",kind.name())
                .query(BigDecimal.class).single();
    }

    public boolean existsByPurchase(UUID purchaseId) {
        return jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM purchase_payments WHERE purchase_id=:purchase)
                """).param("purchase",purchaseId).query(Boolean.class).single();
    }

    public boolean insert(PurchasePayment value) {
        return jdbc.sql("""
                INSERT INTO purchase_payments
                (id,purchase_id,payment_kind,account_code,amount,idempotency_key,
                 request_fingerprint,reference,comment,created_by)
                VALUES (:id,:purchase,:kind,:account,:amount,:key,:fingerprint,
                        :reference,:comment,:actor)
                ON CONFLICT (idempotency_key) DO NOTHING
                """)
                .param("id",value.id())
                .param("purchase",value.purchaseId())
                .param("kind",value.paymentKind().name())
                .param("account",value.account().name())
                .param("amount",value.amount())
                .param("key",value.idempotencyKey())
                .param("fingerprint",value.requestFingerprint())
                .param("reference",value.reference(),Types.VARCHAR)
                .param("comment",value.comment(),Types.VARCHAR)
                .param("actor",value.createdBy()).update() == 1;
    }

    private static PurchasePayment map(ResultSet r,int row) throws SQLException {
        return new PurchasePayment(
                r.getObject("id",UUID.class),r.getObject("purchase_id",UUID.class),
                PurchasePaymentKind.valueOf(r.getString("payment_kind")),
                FinanceAccount.valueOf(r.getString("account_code")),
                r.getBigDecimal("amount"),r.getObject("idempotency_key",UUID.class),
                r.getString("request_fingerprint"),r.getString("reference"),
                r.getString("comment"),r.getString("created_by"),
                r.getTimestamp("created_at").toInstant());
    }
}
