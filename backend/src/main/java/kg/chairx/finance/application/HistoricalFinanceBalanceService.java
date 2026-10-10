package kg.chairx.finance.application;

import kg.chairx.finance.domain.FinanceAccount;
import kg.chairx.finance.persistence.FinanceAccountRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Reconstructs the end-of-day ledger balance using dated journal movements.
 * Callers must hold the affected finance-account row locks: this service does
 * not assert that a raw balance is a correct historical balance.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class HistoricalFinanceBalanceService {
    private final JdbcClient jdbc;
    private final FinanceAccountRepository accounts;

    public HistoricalFinanceBalanceService(JdbcClient jdbc, FinanceAccountRepository accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    public BigDecimal expectedAtEndOf(LocalDate date, FinanceAccount account, BigDecimal lockedCurrent) {
        if (date == null || account == null || lockedCurrent == null) {
            throw new FinanceValidationException("Отчётная дата и финансовый счёт обязательны");
        }
        if (!accounts.isOpeningBalanceInitialized(account)) {
            throw new FinanceConflictException("Финансовый счёт не инициализирован");
        }
        Number problems = jdbc.sql("""
                SELECT count(*) FROM finance_document_posting_issues
                """).query(Number.class).single();
        if (problems.longValue() != 0) {
            throw new FinanceConflictException("UNRECONCILED_FINANCIAL_BALANCE", "найдены финансовые документы без корректной проводки");
        }

        // Every cent of a live balance must be explainable by immutable journal
        // movements, including opening-balance entries.
        BigDecimal accounted = jdbc.sql("""
                SELECT COALESCE(SUM(amount), 0) FROM finance_movements
                WHERE account_code = :account
                """).param("account", account.name()).query(BigDecimal.class).single();
        if (accounted.compareTo(lockedCurrent) != 0) {
            throw new FinanceConflictException(
                    "UNRECONCILED_FINANCIAL_BALANCE: остаток не совпадает с финансовым журналом "
                    + account.name());
        }

        // Prior-to-P21 expenses may have been entered for a different day
        // than the posting date. Never silently reinterpret that legacy data.
        Number uncertainLegacy = jdbc.sql("""
                SELECT count(*) FROM finance_movements m
                JOIN expenses e ON m.source_type = 'EXPENSE' AND e.id = m.source_id
                WHERE m.account_code = :account
                  AND m.business_date_source = 'LEGACY_INFERRED'
                  AND e.expense_date <> m.business_date
                """).param("account", account.name()).query(Number.class).single();
        if (uncertainLegacy.longValue() != 0) {
            throw new FinanceConflictException("UNRECONCILED_FINANCIAL_BALANCE", "исторические расходы с неопределённой отчётной датой");
        }

        BigDecimal subsequentMovements = jdbc.sql("""
                SELECT COALESCE(SUM(amount), 0) FROM finance_movements
                WHERE account_code = :account AND business_date > :date
                """).param("account", account.name()).param("date", date)
                .query(BigDecimal.class).single();

        BigDecimal expected = lockedCurrent.subtract(subsequentMovements);
        if (expected.signum() < 0) {
            throw new FinanceConflictException("UNRECONCILED_FINANCIAL_BALANCE", "отрицательный исторический остаток");
        }
        return expected;
    }
}
