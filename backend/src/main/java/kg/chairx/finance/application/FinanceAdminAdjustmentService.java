package kg.chairx.finance.application;

import kg.chairx.audit.AuditService;
import kg.chairx.finance.api.AdminBalanceAdjustmentRequest;
import kg.chairx.finance.domain.FinanceAccount;
import kg.chairx.finance.persistence.FinanceAccountRepository;
import kg.chairx.finance.persistence.FinanceMovementRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

@Service
public class FinanceAdminAdjustmentService {
    private final FinanceAccountRepository accounts;
    private final FinanceMovementRepository movements;
    private final AuditService audit;
    private final JdbcClient jdbc;

    public FinanceAdminAdjustmentService(FinanceAccountRepository accounts,
            FinanceMovementRepository movements, AuditService audit, JdbcClient jdbc) {
        this.accounts=accounts;
        this.movements=movements;
        this.audit=audit;
        this.jdbc=jdbc;
    }

    public record Result(FinanceAccount account, BigDecimal previousBalance,
            BigDecimal newBalance, BigDecimal delta) { }

    @Transactional
    public Result adjust(AdminBalanceAdjustmentRequest request, String actor) {
        if (request == null || request.account() == null || request.idempotencyKey() == null
                || request.targetBalance() == null || request.targetBalance().signum() < 0
                || request.targetBalance().stripTrailingZeros().scale() > 0
                || request.reason() == null || request.reason().strip().length() < 5
                || request.reason().strip().length() > 500
                || actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Некорректная финансовая корректировка");
        }

        // Account-row lock serializes concurrent adjustments and normal postings.
        BigDecimal before = accounts.lockBalance(request.account());
        var existing=jdbc.sql("""
                SELECT account_code, previous_balance, new_balance, delta, reason
                FROM finance_admin_adjustments WHERE id=:id
                """).param("id",request.idempotencyKey())
                .query((rs,row)->new Stored(
                        rs.getString("account_code"),rs.getBigDecimal("previous_balance"),
                        rs.getBigDecimal("new_balance"),rs.getBigDecimal("delta"),
                        rs.getString("reason"))).optional().orElse(null);
        if (existing != null) {
            if (!existing.account().equals(request.account().name())
                    || existing.newBalance().compareTo(request.targetBalance()) != 0
                    || !existing.reason().equals(request.reason().strip())) {
                throw new IllegalStateException("Ключ финансовой корректировки уже использован с другими данными");
            }
            return new Result(request.account(), existing.previousBalance(),
                    existing.newBalance(),existing.delta());
        }

        if (!accounts.isOpeningBalanceInitialized(request.account())) {
            throw new IllegalStateException("Сначала необходимо инициализировать финансовый счёт");
        }
        BigDecimal delta=request.targetBalance().subtract(before);
        if (delta.signum() == 0) {
            throw new IllegalArgumentException("Сумма уже совпадает с остатком; корректировка не требуется");
        }
        // Built-in repository guard refuses a closed financial day or a negative account.
        accounts.changeBalance(request.account(),delta);
        int inserted=jdbc.sql("""
                INSERT INTO finance_admin_adjustments
                    (id,account_code,previous_balance,new_balance,delta,reason,created_by)
                VALUES (:id,:account,:old,:new,:delta,:reason,:actor)
                ON CONFLICT (id) DO NOTHING
                """).param("id",request.idempotencyKey())
                .param("account",request.account().name())
                .param("old",before).param("new",request.targetBalance()).param("delta",delta)
                .param("reason",request.reason().strip()).param("actor",actor)
                .update();
        if(inserted != 1) {
            throw new IllegalStateException("Конкурирующая корректировка с таким ключом уже проведена");
        }
        movements.insert(UUID.randomUUID(),request.account(),delta,
                "ADMIN_BALANCE_ADJUSTMENT","ADMIN_BALANCE_ADJUSTMENT",
                request.idempotencyKey(),actor);
        var result=new Result(request.account(),before,request.targetBalance(),delta);
        audit.recordAs(actor,"FINANCE_ADMIN_ADJUSTMENT",request.idempotencyKey(),
                "CREATED",null,new AdjustmentAudit(result,request.reason().strip()));
        return result;
    }

    private record Stored(String account,BigDecimal previousBalance,
            BigDecimal newBalance,BigDecimal delta,String reason) {}
    private record AdjustmentAudit(Result amounts,String reason) {}
}
