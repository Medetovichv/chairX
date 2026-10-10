package kg.chairx.finance.application;

import kg.chairx.audit.AuditService;
import kg.chairx.finance.api.DailyClosingRequest;
import kg.chairx.finance.api.DailyClosingResponse;
import kg.chairx.finance.api.DailyClosingUpdateRequest;
import kg.chairx.finance.domain.FinanceAccount;
import kg.chairx.finance.persistence.FinanceAccountRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DailyClosingService {
    private final JdbcClient jdbc;
    private final FinanceAccountRepository accounts;
    private final HistoricalFinanceBalanceService historical;
    private final DailyClosingAccessService access;
    private final AuditService audit;
    private final DailyClosingAccessPolicy policy;

    public DailyClosingService(JdbcClient jdbc, FinanceAccountRepository accounts,
                               HistoricalFinanceBalanceService historical,
                               DailyClosingAccessService access, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.historical = historical;
        this.access = access;
        this.audit = audit;
        this.policy = new DailyClosingAccessPolicy(clock);
    }

    /** Existing trusted Java service API. HTTP writes use closeAuthorized. */
    @Transactional
    public DailyClosingResponse close(LocalDate date, DailyClosingRequest request, String actor) {
        return closeInternal(date, request, actor, false);
    }

    /** Authorization must be rechecked under locks; routing permission alone is insufficient. */
    @Transactional
    public DailyClosingResponse closeAuthorized(LocalDate date, DailyClosingRequest request, String actor) {
        return closeInternal(date, request, actor, true);
    }

    private DailyClosingResponse closeInternal(LocalDate date, DailyClosingRequest request,
                                               String actor, boolean authorizedHttpWrite) {
        validateCommon(date, actor);
        if (request == null) {
            throw new FinanceValidationException("Параметры закрытия обязательны");
        }
        validate(request.actualCash(), request.cashNote());
        validate(request.actualBank(), request.bankNote());

        // Existing lock order is BANK, then CASH. Never reverse it.
        BigDecimal liveBank = accounts.lockBalance(FinanceAccount.BANK);
        BigDecimal liveCash = accounts.lockBalance(FinanceAccount.CASH);
        access.lockDate(date);
        assertWindow(date, authorizedHttpWrite);

        if (!accounts.isOpeningBalanceInitialized(FinanceAccount.CASH)
                || !accounts.isOpeningBalanceInitialized(FinanceAccount.BANK)) {
            throw new FinanceConflictException("Финансовые счета не инициализированы");
        }
        if (jdbc.sql("SELECT EXISTS (SELECT 1 FROM finance_daily_closings WHERE business_date = :day)")
                .param("day", date).query(Boolean.class).single()) {
            throw new FinanceConflictException("REPORT_ALREADY_EXISTS", "День уже закрыт");
        }

        BigDecimal bank = expected(date, FinanceAccount.BANK, liveBank);
        BigDecimal cash = expected(date, FinanceAccount.CASH, liveCash);
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO finance_daily_closings (id, business_date, created_by) VALUES (:id, :day, :actor)")
                .param("id", id).param("day", date).param("actor", actor).update();
        insertAccount(id, FinanceAccount.CASH, cash, request.actualCash(), request.cashNote());
        insertAccount(id, FinanceAccount.BANK, bank, request.actualBank(), request.bankNote());
        DailyClosingResponse created = findByDate(date);
        audit.recordAs(actor, "FINANCE_DAILY_CLOSING", id, "REPORT_CLOSED", null, created);
        return created;
    }

    private void validateCommon(LocalDate date, String actor) {
        if (date == null || actor == null || actor.isBlank() || actor.length() > 200) {
            throw new FinanceValidationException("Некорректные параметры финансового отчёта");
        }
        if (date.isAfter(policy.today())) {
            throw new FinanceValidationException("Нельзя закрыть будущую дату");
        }
    }

    private void assertWindow(LocalDate date, boolean authorizedHttpWrite) {
        // Re-read time after waiting for money/date locks.
        if (authorizedHttpWrite) {
            access.assertCanEdit(date);
        } else if (!policy.normalWriteWindow(date, policy.now())) {
            throw new FinanceValidationException("REPORT_LOCKED: отчётный день недоступен");
        }
    }

    private BigDecimal expected(LocalDate date, FinanceAccount account, BigDecimal liveBalance) {
        // Today's close uses the live balance; yesterday's uses dated journal
        // movements and must first prove that the journal reconciles.
        return date.equals(policy.today()) ? liveBalance
                : historical.expectedAtEndOf(date, account, liveBalance);
    }

    private static void validate(BigDecimal actual, String note) {
        if (actual == null || actual.signum() < 0 || actual.stripTrailingZeros().scale() > 0) {
            throw new FinanceValidationException("Фактический остаток должен быть неотрицательным целым числом");
        }
        if (note != null && note.length() > 2000) {
            throw new FinanceValidationException("Заметка слишком длинная");
        }
    }

    private static String normalized(String note) {
        return note == null ? null : note.trim();
    }

    private void insertAccount(UUID id, FinanceAccount account, BigDecimal expected,
                               BigDecimal actual, String note) {
        if (expected.compareTo(actual) != 0 && (note == null || note.isBlank())) {
            throw new FinanceValidationException("При расхождении необходимо указать причину");
        }
        jdbc.sql("""
                INSERT INTO finance_daily_closing_accounts
                  (closing_id, account_code, expected_balance, actual_balance, note)
                VALUES (:id, :account, :expected, :actual, :note)
                """).param("id", id).param("account", account.name())
                .param("expected", expected).param("actual", actual)
                .param("note", normalized(note)).update();
    }

    /** Update observations, not the immutable financial ledger or account balances. */
    @Transactional
    public DailyClosingResponse update(LocalDate date, DailyClosingUpdateRequest request, String actor) {
        validateCommon(date, actor);
        if (request == null || request.expectedVersion() < 0
                || request.reason() == null || request.reason().isBlank()
                || request.reason().length() > 2000) {
            throw new FinanceValidationException("Укажите версию отчёта и причину исправления");
        }
        validate(request.actualCash(), request.cashNote());
        validate(request.actualBank(), request.bankNote());
        access.lockDate(date);
        access.assertCanEdit(date);
        DailyClosingResponse before = findByDate(date);
        if (before.version() != request.expectedVersion()) {
            throw new FinanceConflictException("REPORT_VERSION_CONFLICT", "Отчёт изменён другим сотрудником");
        }
        int count = jdbc.sql("""
                UPDATE finance_daily_closings SET version = version + 1
                WHERE id = :id AND version = :version
                """).param("id", before.id()).param("version", request.expectedVersion()).update();
        if (count != 1) {
            throw new FinanceConflictException("REPORT_VERSION_CONFLICT", "Отчёт изменён другим сотрудником");
        }
        updateObservation(before.id(), FinanceAccount.CASH, request.actualCash(), request.cashNote());
        updateObservation(before.id(), FinanceAccount.BANK, request.actualBank(), request.bankNote());
        // Even after a slow request, an expired grant must not authorize a commit.
        access.assertCanEdit(date);
        DailyClosingResponse after = findByDate(date);
        audit.recordAs(actor, "FINANCE_DAILY_CLOSING", before.id(), "REPORT_UPDATED",
                Map.of("reason", request.reason(), "report", before),
                Map.of("reason", request.reason(), "report", after));
        return after;
    }

    private void updateObservation(UUID report, FinanceAccount account, BigDecimal actual, String note) {
        BigDecimal expected = jdbc.sql("""
                SELECT expected_balance FROM finance_daily_closing_accounts
                WHERE closing_id = :id AND account_code = :account
                """).param("id", report).param("account", account.name())
                .query(BigDecimal.class).single();
        if (expected.compareTo(actual) != 0 && (note == null || note.isBlank())) {
            throw new FinanceValidationException("При расхождении необходимо указать причину");
        }
        jdbc.sql("""
                UPDATE finance_daily_closing_accounts
                SET actual_balance = :actual, note = :note
                WHERE closing_id = :id AND account_code = :account
                """).param("actual", actual).param("note", normalized(note))
                .param("id", report).param("account", account.name()).update();
    }

    /** Called only inside a controlled, serialized historical-expense transaction. */
    @Transactional
    public DailyClosingResponse refreshExpectedAfterCorrection(LocalDate date, String actor) {
        DailyClosingResponse before = findByDate(date);
        boolean laterClosed = jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM finance_daily_closings WHERE business_date > :date)
                """).param("date", date).query(Boolean.class).single();
        if (laterClosed) {
            throw new FinanceConflictException(
                    "HISTORICAL_POSTING_NOT_ALLOWED", "Более поздние отчёты уже закрыты");
        }
        BigDecimal bank = historical.expectedAtEndOf(date, FinanceAccount.BANK,
                accounts.lockBalance(FinanceAccount.BANK));
        BigDecimal cash = historical.expectedAtEndOf(date, FinanceAccount.CASH,
                accounts.lockBalance(FinanceAccount.CASH));
        jdbc.sql("""
                UPDATE finance_daily_closing_accounts
                SET expected_balance = CASE account_code WHEN 'CASH' THEN :cash ELSE :bank END
                WHERE closing_id = :id
                """).param("id", before.id()).param("cash", cash).param("bank", bank).update();
        jdbc.sql("UPDATE finance_daily_closings SET version = version + 1 WHERE id = :id")
                .param("id", before.id()).update();
        DailyClosingResponse after = findByDate(date);
        audit.recordAs(actor, "FINANCE_DAILY_CLOSING", before.id(), "REPORT_EXPENSE_CORRECTED",
                before, after);
        return after;
    }

    /** Preview acquires account locks so balances cannot race an actual posting. */
    @Transactional
    public Preview preview(LocalDate date) {
        if (date == null || date.isAfter(policy.today())) {
            throw new FinanceValidationException("Недопустимая отчётная дата");
        }
        BigDecimal bank = accounts.lockBalance(FinanceAccount.BANK);
        BigDecimal cash = accounts.lockBalance(FinanceAccount.CASH);
        return new Preview(date, expected(date, FinanceAccount.CASH, cash),
                expected(date, FinanceAccount.BANK, bank));
    }

    @Transactional(readOnly = true)
    public DailyClosingResponse findByDate(LocalDate date) {
        var rows = jdbc.sql("""
                SELECT c.id, c.business_date, c.created_at, c.created_by, c.version,
                       a.account_code, a.expected_balance, a.actual_balance, a.difference, a.note
                FROM finance_daily_closings c
                JOIN finance_daily_closing_accounts a ON a.closing_id = c.id
                WHERE c.business_date = :date ORDER BY a.account_code
                """).param("date", date).query((rs, row) -> new ClosingRow(
                (UUID) rs.getObject("id"), rs.getDate("business_date").toLocalDate(),
                rs.getTimestamp("created_at").toInstant(), rs.getString("created_by"),
                rs.getLong("version"), rs.getString("account_code"), new DailyClosingResponse.Account(
                        rs.getBigDecimal("expected_balance"), rs.getBigDecimal("actual_balance"),
                        rs.getBigDecimal("difference"), rs.getString("note")))).list();
        if (rows.size() != 2) {
            throw new ClosingNotFoundException("Закрытие дня не найдено");
        }
        ClosingRow first = rows.getFirst();
        var cash = rows.stream().filter(r -> r.account.equals("CASH")).findFirst().orElseThrow().value;
        var bank = rows.stream().filter(r -> r.account.equals("BANK")).findFirst().orElseThrow().value;
        return new DailyClosingResponse(first.id, first.date, first.createdAt,
                first.actor, cash, bank, first.version);
    }

    @Transactional(readOnly = true)
    public List<LocalDate> closedDates() {
        return jdbc.sql("SELECT business_date FROM finance_daily_closings ORDER BY business_date DESC LIMIT 100")
                .query((rs, row) -> rs.getDate(1).toLocalDate()).list();
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> history(LocalDate date) {
        UUID reportId = jdbc.sql("SELECT id FROM finance_daily_closings WHERE business_date = :date")
                .param("date", date).query(UUID.class).optional().orElse(null);
        UUID provisionalId = UUID.nameUUIDFromBytes(("FINANCE_DAILY_CLOSING:" + date)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return jdbc.sql("""
                SELECT action, actor, occurred_at, before_state::text AS before_state,
                       after_state::text AS after_state
                FROM audit_entries
                WHERE entity_type = 'FINANCE_DAILY_CLOSING'
                  AND (entity_id = :provisional OR entity_id = :report)
                ORDER BY occurred_at ASC, id
                """).param("provisional", provisionalId)
                .param("report", reportId, java.sql.Types.OTHER)
                .query((rs, row) -> new AuditEntry(rs.getString("action"), rs.getString("actor"),
                        rs.getTimestamp("occurred_at").toInstant(), rs.getString("before_state"),
                        rs.getString("after_state"))).list();
    }

    public record Preview(LocalDate businessDate, BigDecimal expectedCash, BigDecimal expectedBank) {}
    public record AuditEntry(String action, String actor, Instant at, String before, String after) {}
    private record ClosingRow(UUID id, LocalDate date, Instant createdAt, String actor,
                              long version, String account, DailyClosingResponse.Account value) {}
}
