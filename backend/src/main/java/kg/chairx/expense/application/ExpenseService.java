package kg.chairx.expense.application;

import kg.chairx.finance.application.FinancePostingService;
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

    public ExpenseService(
            FinancePostingService finance,
            ExpenseRepository repository) {
        this.finance = finance;
        this.repository = repository;
    }

    /**
     * Создание нового расхода.
     */
    @Transactional
    public ExpenseResponse create(CreateExpenseRequest request) {

        // Проверяем обязательные поля
        if (request == null
                || request.category() == null
                || request.paymentMethod() == null
                || request.expenseDate() == null) {

            throw new ExpenseValidationException(
                    "Не заполнены обязательные поля расхода"
            );
        }

        // Проверяем сумму расхода
        BigDecimal amount = request.amount();

        if (amount == null
                || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 0) {

            throw new ExpenseValidationException(
                    "Сумма должна быть положительным целым числом сомов"
            );
        }

        // Нормализуем комментарий
        String comment = request.comment();

        if (comment != null) {
            comment = comment.trim();

            if (comment.isEmpty()) {
                comment = null;
            } else if (comment.length() > 1000) {

                throw new ExpenseValidationException(
                        "Комментарий не должен превышать 1000 символов"
                );
            }
        }

        // Создаём расход
        Expense expense = new Expense(
                UUID.randomUUID(),
                request.category(),
                amount.setScale(0),
                request.paymentMethod(),
                request.expenseDate(),
                comment,
                actor(),
                Instant.now()
        );

        // Сохраняем в PostgreSQL
        repository.insert(expense);
        finance.post(expense.paymentMethod().name(), expense.amount().negate(), "EXPENSE", "EXPENSE", expense.id(), expense.createdBy());

        return ExpenseResponse.from(expense);
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