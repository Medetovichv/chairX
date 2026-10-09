package kg.chairx.finance.application;

import kg.chairx.finance.api.DailyClosingRequest;
import kg.chairx.finance.api.DailyClosingResponse;
import kg.chairx.finance.domain.FinanceAccount;
import kg.chairx.finance.persistence.FinanceAccountRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class DailyClosingService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Bishkek");
    private final JdbcClient jdbc;
    private final FinanceAccountRepository accounts;
    private final Clock clock;

    public DailyClosingService(JdbcClient jdbc, FinanceAccountRepository accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public DailyClosingResponse close(LocalDate date, DailyClosingRequest request, String actor) {
        if (date == null || request == null || actor == null || actor.isBlank() || actor.length() > 200) {
            throw new IllegalArgumentException("Некорректные параметры закрытия");
        }
        if (!date.equals(LocalDate.now(clock.withZone(BUSINESS_ZONE)))) {
            throw new IllegalArgumentException("Разрешено закрывать только текущий день по Бишкеку");
        }
        validate(request.actualCash(), request.cashNote());
        validate(request.actualBank(), request.bankNote());


        // Serialize all closings and financial writes using the existing account locks.
        BigDecimal cash = accounts.lockBalance(FinanceAccount.CASH);
        BigDecimal bank = accounts.lockBalance(FinanceAccount.BANK);
        if (!accounts.isOpeningBalanceInitialized(FinanceAccount.CASH)
                || !accounts.isOpeningBalanceInitialized(FinanceAccount.BANK)) {
            throw new IllegalStateException("Финансовые счета не инициализированы");
        }
        if (jdbc.sql("SELECT EXISTS (SELECT 1 FROM finance_daily_closings WHERE business_date = :date)")
                .param("date", date).query(Boolean.class).single()) {
            throw new IllegalStateException("День уже закрыт");
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO finance_daily_closings (id, business_date, created_by) VALUES (:id, :date, :actor)")
                .param("id", id).param("date", date).param("actor", actor).update();
        insertAccount(id, FinanceAccount.CASH, cash, request.actualCash(), request.cashNote());
        insertAccount(id, FinanceAccount.BANK, bank, request.actualBank(), request.bankNote());
        return findByDate(date);
    }

    private static void validate(BigDecimal actual, String note) {
        if (actual == null || actual.signum() < 0 || actual.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Фактический остаток должен быть неотрицательным целым числом");
        }
        if (note != null && note.length() > 2000) {
            throw new IllegalArgumentException("Заметка слишком длинная");
        }
    }

    private void insertAccount(UUID id, FinanceAccount account, BigDecimal expected,
                               BigDecimal actual, String note) {
        if (expected.compareTo(actual) != 0 && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("При расхождении необходимо указать причину");
        }
        jdbc.sql("""
                INSERT INTO finance_daily_closing_accounts
                  (closing_id, account_code, expected_balance, actual_balance, note)
                VALUES (:id, :account, :expected, :actual, :note)
                """).param("id", id).param("account", account.name())
                .param("expected", expected).param("actual", actual)
                .param("note", note == null ? null : note.trim()).update();
    }

    @Transactional(readOnly = true)
    public DailyClosingResponse findByDate(LocalDate date) {
        var rows = jdbc.sql("""
                SELECT c.id, c.business_date, c.created_at, c.created_by,
                       a.account_code, a.expected_balance, a.actual_balance, a.difference, a.note
                FROM finance_daily_closings c
                JOIN finance_daily_closing_accounts a ON a.closing_id = c.id
                WHERE c.business_date = :date ORDER BY a.account_code
                """).param("date", date).query((rs, row) -> new ClosingRow(
                (UUID) rs.getObject("id"), rs.getDate("business_date").toLocalDate(),
                rs.getTimestamp("created_at").toInstant(), rs.getString("created_by"),
                rs.getString("account_code"), new DailyClosingResponse.Account(
                        rs.getBigDecimal("expected_balance"), rs.getBigDecimal("actual_balance"),
                        rs.getBigDecimal("difference"), rs.getString("note")))).list();
        if (rows.size() != 2) {
            throw new IllegalArgumentException("Закрытие дня не найдено");
        }
        ClosingRow first = rows.getFirst();
        var cash = rows.stream().filter(r -> r.account.equals("CASH")).findFirst().orElseThrow().value;
        var bank = rows.stream().filter(r -> r.account.equals("BANK")).findFirst().orElseThrow().value;
        return new DailyClosingResponse(first.id, first.date, first.createdAt, first.actor, cash, bank);
    }

    @Transactional(readOnly = true)
    public List<LocalDate> closedDates() {
        return jdbc.sql("SELECT business_date FROM finance_daily_closings ORDER BY business_date DESC LIMIT 100")
                .query((rs, row) -> rs.getDate(1).toLocalDate()).list();
    }

    private record ClosingRow(UUID id, LocalDate date, java.time.Instant createdAt,
                              String actor, String account, DailyClosingResponse.Account value) {}
}
