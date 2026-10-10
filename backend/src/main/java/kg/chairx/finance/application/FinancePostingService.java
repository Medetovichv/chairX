package kg.chairx.finance.application;

import kg.chairx.finance.domain.FinanceAccount;
import kg.chairx.finance.persistence.FinanceAccountRepository;
import kg.chairx.finance.persistence.FinanceMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

/** Financial boundary: document, balance and journal share the caller's transaction. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class FinancePostingService {
    private final FinanceAccountRepository accounts;
    private final FinanceMovementRepository movements;
    private final DailyClosingAccessService closingAccess;
    private final JdbcClient jdbc;

    @Autowired
    public FinancePostingService(FinanceAccountRepository accounts,
                                 FinanceMovementRepository movements,
                                 DailyClosingAccessService closingAccess, JdbcClient jdbc) {
        this.accounts = accounts;
        this.movements = movements;
        this.closingAccess = closingAccess;
        this.jdbc = jdbc;
    }

    /** Compatibility for isolated posting-service tests not using corrections. */
    public FinancePostingService(FinanceAccountRepository accounts, FinanceMovementRepository movements) {
        this(accounts, movements, null, null);
    }

    public static FinanceAccount account(String method) {
        return switch (method) {
            case "CASH" -> FinanceAccount.CASH;
            case "TRANSFER", "BANK" -> FinanceAccount.BANK;
            default -> throw new IllegalArgumentException("Неизвестный способ оплаты");
        };
    }

    public void post(String method, BigDecimal amount, String type, String source, UUID sourceId, String actor) {
        FinanceAccount account = account(method);
        accounts.lockBalance(account);
        if (!accounts.isOpeningBalanceInitialized(account)) {
            throw new FinancePostingException("Сначала необходимо инициализировать финансовый счёт");
        }
        if (movements.exists(source, sourceId, account)) {
            throw new FinancePostingException("Документ уже проведён по финансовому счёту");
        }
        accounts.changeBalance(account, amount);
        movements.insert(UUID.randomUUID(), account, amount, type, source, sourceId, actor);
    }

    /**
     * Expense business dates cannot be changed after that period has closed.
     * Lock the same account row used by the daily closing BEFORE inspecting
     * the closing dates, so this check and the posting cannot race a close.
     */
    public void postExpense(String method, BigDecimal amount, UUID expenseId,
                            String actor, java.time.LocalDate expenseDate) {
        FinanceAccount account = account(method);
        accounts.lockBalance(account);
        accounts.rejectClosedDocumentDate(expenseDate);
        if (expenseDate == null) {
            throw new FinanceValidationException("Дата расхода обязательна");
        }
        if (!accounts.isOpeningBalanceInitialized(account)) {
            throw new FinancePostingException("Сначала необходимо инициализировать финансовый счёт");
        }
        if (movements.exists("EXPENSE", expenseId, account)) {
            throw new FinancePostingException("Расход уже проведён");
        }
        accounts.changeBalance(account, amount);
        java.time.LocalDate postedDate = java.time.LocalDate.now(
                java.time.ZoneId.of("Asia/Bishkek"));
        boolean backdated = expenseDate.isBefore(postedDate);
        movements.insert(UUID.randomUUID(), account, amount, "EXPENSE", "EXPENSE", expenseId,
                actor, backdated ? expenseDate : postedDate,
                backdated ? "HISTORICAL_CORRECTION" : "POSTING_DATE");
    }

    /**
     * Scoped exception to the closed-document-date guard. The original
     * postExpense path deliberately remains fully sealed.
     *
     * Financial account locks, date lock and authorization precede the
     * write and serialize with closes, transfers and other postings.
     */
    public void assertCorrectionPermitted(java.time.LocalDate date, String actor) {
        if (closingAccess == null || jdbc == null || actor == null) {
            throw new FinancePostingException("Историческая корректировка недоступна");
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !actor.equals(authentication.getName())
                || authentication.getAuthorities().stream().noneMatch(a ->
                    "DAILY_CLOSING_EXPENSE_CORRECT".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Нет права добавлять забытые расходы");
        }
        accounts.lockBalance(FinanceAccount.BANK);
        accounts.lockBalance(FinanceAccount.CASH);
        closingAccess.lockDate(date);
        closingAccess.assertCanEdit(date);
        boolean exists = jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM finance_daily_closings
                              WHERE business_date = :date)
                """).param("date", date).query(Boolean.class).single();
        if (!exists) {
            throw new FinanceConflictException("REPORT_NOT_FOUND", "Сначала закройте отчёт");
        }
        boolean laterClosed = jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM finance_daily_closings
                              WHERE business_date > :date)
                """).param("date", date).query(Boolean.class).single();
        if (laterClosed) {
            throw new FinanceConflictException(
                    "HISTORICAL_POSTING_NOT_ALLOWED", "Последующие дни уже закрыты");
        }
    }

    public void postHistoricalExpense(String method, BigDecimal amount, UUID expenseId,
                                      String actor, java.time.LocalDate date) {
        assertCorrectionPermitted(date, actor);
        FinanceAccount account = account(method);
        if (!accounts.isOpeningBalanceInitialized(account)) {
            throw new FinancePostingException("Сначала инициализируйте финансовый счёт");
        }
        if (movements.exists("EXPENSE", expenseId, account)) {
            throw new FinancePostingException("Расход уже проведён");
        }
        // No exemption from the GLOBAL current-day financial lock:
        // changeBalance itself rejects posting after today's close.
        accounts.changeBalance(account, amount);
        movements.insert(UUID.randomUUID(), account, amount, "EXPENSE",
                "EXPENSE", expenseId, actor, date, "HISTORICAL_CORRECTION");
    }

    public void reversePayment(String method, BigDecimal amount, UUID paymentId, String actor) {
        // Older documents were not posted. Never debit their amount automatically.
        if (!movements.exists("PAYMENT", paymentId, account(method))) {
            throw new FinancePostingException("Историческая оплата без проводки требует финансовой сверки");
        }
        post(method, amount.negate(), "PAYMENT_REVERSAL", "PAYMENT_REVERSAL", paymentId, actor);
    }
}
