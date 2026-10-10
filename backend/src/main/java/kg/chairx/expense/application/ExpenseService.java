package kg.chairx.expense.application;

import kg.chairx.finance.application.FinancePostingService;
import kg.chairx.finance.application.DailyClosingService;
import kg.chairx.expense.api.CreateExpenseRequest;
import kg.chairx.expense.api.ExpenseResponse;
import kg.chairx.expense.domain.Expense;
import kg.chairx.expense.persistence.ExpenseRepository;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ExpenseService {

    private final FinancePostingService finance;
    private final ExpenseRepository repository;
    private final DailyClosingService closing;

    @org.springframework.beans.factory.annotation.Autowired
    public ExpenseService(FinancePostingService finance, ExpenseRepository repository,
                          DailyClosingService closing) {
        this.finance = finance;
        this.repository = repository;
        this.closing = closing;
    }

    /** Compatibility for standalone unit tests of ordinary expenses. */
    public ExpenseService(FinancePostingService finance, ExpenseRepository repository) {
        this(finance, repository, null);
    }

    /**
     * Создание нового расхода.
     */
    @Transactional
    public ExpenseResponse create(CreateExpenseRequest request) {
        return createInternal(request, false);
    }

    /** Only callable through the date-scoped daily-closing permission path. */
    @Transactional
    public ExpenseResponse createForClosing(LocalDate closingDate, CreateExpenseRequest request) {
        if (request == null || closingDate == null || !closingDate.equals(request.expenseDate())) {
            throw new ExpenseValidationException("Дата расхода не совпадает с отчётной датой");
        }
        finance.assertCorrectionPermitted(closingDate, actor());
        return createInternal(request, true);
    }

    private ExpenseResponse createInternal(CreateExpenseRequest request, boolean reportCorrection) {
        if (request == null || request.idempotencyKey() == null
                || request.category() == null || request.paymentMethod() == null
                || request.expenseDate() == null) {
            throw new ExpenseValidationException("Не заполнены обязательные поля расхода");
        }
        BigDecimal amount = request.amount();
        if (amount == null || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 0) {
            throw new ExpenseValidationException(
                    "Сумма должна быть положительным целым числом сомов");
        }
        String comment = normalizeComment(request.comment());
        BigDecimal normalizedAmount = amount.setScale(0);
        String fingerprint = fingerprint(request, normalizedAmount, comment);

        // Safe fast replay path. A closed business day does not prevent reading
        // an already committed operation. No second financial posting occurs.
        var existing = repository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), request, normalizedAmount, comment, fingerprint);
        }

        Expense expense = new Expense(
                UUID.randomUUID(), request.category(), normalizedAmount,
                request.paymentMethod(), request.expenseDate(), comment,
                actor(), Instant.now()
        );

        // Unique index makes concurrent retries wait for the winning
        // transaction to commit. The losing request never posts money.
        if (!repository.tryInsert(expense, request.idempotencyKey(), fingerprint)) {
            Expense committed = repository.findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "Ключ расхода занят, но документ не найден"));
            return replay(committed, request, normalizedAmount, comment, fingerprint);
        }

        if (reportCorrection) {
            finance.postHistoricalExpense(expense.paymentMethod().name(), expense.amount().negate(),
                    expense.id(), expense.createdBy(), expense.expenseDate());
            if (closing == null) {
                throw new IllegalStateException("Daily closing service not configured");
            }
            closing.refreshExpectedAfterCorrection(expense.expenseDate(), expense.createdBy());
            finance.assertCorrectionPermitted(expense.expenseDate(), expense.createdBy());
        } else {
            finance.postExpense(expense.paymentMethod().name(), expense.amount().negate(),
                    expense.id(), expense.createdBy(), expense.expenseDate());
        }

        return ExpenseResponse.from(expense);
    }

    private ExpenseResponse replay(
            Expense previous, CreateExpenseRequest request, BigDecimal amount,
            String comment, String fingerprint
    ) {
        String storedFingerprint = repository.requestFingerprint(previous.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Отсутствует fingerprint расхода с ключом"));
        if (!storedFingerprint.equals(fingerprint)
                || previous.category() != request.category()
                || previous.paymentMethod() != request.paymentMethod()
                || previous.amount().compareTo(amount) != 0
                || !previous.expenseDate().equals(request.expenseDate())
                || !java.util.Objects.equals(previous.comment(), comment)) {
            throw new ExpenseConflictException(
                    "EXPENSE_IDEMPOTENCY_CONFLICT",
                    "Ключ операции уже использован для другого расхода");
        }
        return ExpenseResponse.from(previous);
    }

    private static String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String value = comment.trim();
        if (value.length() > 1000) {
            throw new ExpenseValidationException(
                    "Комментарий не должен превышать 1000 символов");
        }
        return value;
    }

    /** Length-prefixed fields prevent delimiter and null/empty collisions. */
    private static String fingerprint(
            CreateExpenseRequest request, BigDecimal amount, String comment
    ) {
        StringBuilder canonical = new StringBuilder();
        for (String value : new String[]{
                request.category().name(), amount.toPlainString(),
                request.paymentMethod().name(), request.expenseDate().toString(),
                comment
        }) {
            if (value == null) {
                canonical.append("-1:");
            } else {
                canonical.append(value.length()).append(':').append(value);
            }
        }
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 недоступен", error);
        }
    }

    /**
     * Получение расхода по ID.
     */
    @Transactional(readOnly = true)
    public ExpenseResponse get(UUID id) {

        return repository.find(id)
                .map(ExpenseResponse::from)
                .orElseThrow(ExpenseNotFoundException::new);
    }

    /**
     * Список расходов за выбранный период.
     *
     * from включительно
     * to исключительно
     */
    @Transactional(readOnly = true)
    public List<ExpenseResponse> list(
            LocalDate from,
            LocalDate to
    ) {
        validatePeriod(from, to);

        return repository.findByPeriod(from, to)
                .stream()
                .map(ExpenseResponse::from)
                .toList();
    }

    /**
     * Общая сумма расходов за период.
     */
    @Transactional(readOnly = true)
    public BigDecimal total(
            LocalDate from,
            LocalDate to
    ) {
        validatePeriod(from, to);

        return repository.totalByPeriod(from, to);
    }

    /**
     * Проверка корректности периода.
     */
    private static void validatePeriod(
            LocalDate from,
            LocalDate to
    ) {
        if (from == null
                || to == null
                || !from.isBefore(to)) {

            throw new ExpenseValidationException(
                    "Период должен быть корректным: from < to"
            );
        }
    }

    /**
     * Получаем имя авторизованного сотрудника.
     */
    private static String actor() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new IllegalStateException(
                    "Для регистрации расхода требуется авторизация"
            );
        }

        return authentication.getName();
    }
}