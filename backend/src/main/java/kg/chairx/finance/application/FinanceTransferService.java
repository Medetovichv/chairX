package kg.chairx.finance.application;

import kg.chairx.finance.domain.FinanceAccount;
import kg.chairx.finance.domain.InsufficientFundsException;
import kg.chairx.finance.persistence.FinanceAccountRepository;
import kg.chairx.finance.persistence.FinanceMovementRepository;
import kg.chairx.finance.persistence.FinanceTransferRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class FinanceTransferService {

    private final FinanceAccountRepository accounts;
    private final FinanceMovementRepository movements;
    private final FinanceTransferRepository transfers;

    public FinanceTransferService(
            FinanceAccountRepository accounts,
            FinanceMovementRepository movements,
            FinanceTransferRepository transfers
    ) {
        this.accounts = accounts;
        this.movements = movements;
        this.transfers = transfers;
    }

    @Transactional
    public UUID transfer(
            UUID transferId,
            FinanceAccount from,
            FinanceAccount to,
            BigDecimal amount,
            String actor
    ) {
        // 1. Проверяем входные данные

        if (transferId == null) {
            throw new FinanceValidationException(
                    "Необходимо указать ID перевода"
            );
        }

        if (from == null || to == null || from == to) {
            throw new FinanceValidationException(
                    "Некорректные счета"
            );
        }

        if (amount == null
                || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 0) {
            throw new FinanceValidationException(
                    "Сумма должна быть положительной и целой"
            );
        }

        if (actor == null
                || actor.isBlank()
                || actor.length() > 200) {
            throw new FinanceValidationException(
                    "Некорректный сотрудник"
            );
        }

        // 2. Определяем единый порядок блокировки счетов.
        // Это предотвращает взаимную блокировку при
        // одновременных переводах CASH -> BANK и BANK -> CASH.

        FinanceAccount first =
                from.name().compareTo(to.name()) < 0
                        ? from
                        : to;

        FinanceAccount second =
                first == from ? to : from;

        // 3. Блокируем оба счёта до регистрации перевода.
        // Блокировки удерживаются до завершения транзакции.

        accounts.lockBalance(first);
        accounts.lockBalance(second);

        // 4. Регистрируем перевод.
        // PRIMARY KEY в finance_transfers защищает от
        // повторного создания операции с тем же UUID.

        boolean inserted = transfers.insertIfAbsent(
                transferId,
                from,
                to,
                amount,
                actor
        );

        // 5. Обрабатываем повторный запрос.

        if (!inserted) {
            var existing = transfers.findById(transferId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Перевод существует, но не найден"
                    ));

            if (existing.from() != from
                    || existing.to() != to
                    || existing.amount().compareTo(amount) != 0) {

                throw new FinanceConflictException(
                        "ID перевода уже используется с другими параметрами"
                );
            }

            // Операция уже выполнена.
            // Повторно деньги не списываем.
            return transferId;
        }

        // 6. Проверяем инициализацию обоих счетов.

        if (!accounts.isOpeningBalanceInitialized(from)
                || !accounts.isOpeningBalanceInitialized(to)) {

            throw new FinanceConflictException(
                    "Сначала необходимо инициализировать оба счёта"
            );
        }

        // 7. Проверяем достаточность средств.
        // Счёт уже заблокирован, поэтому параллельная
        // транзакция не сможет изменить остаток.

        BigDecimal available = accounts.balance(from);

        if (available.compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }

        // 8. Изменяем остатки.
        // Обе операции выполняются в одной транзакции.

        accounts.changeBalance(from, amount.negate());
        accounts.changeBalance(to, amount);

        // 9. Записываем списание в финансовый журнал.

        movements.insert(
                UUID.randomUUID(),
                from,
                amount.negate(),
                "TRANSFER",
                "TRANSFER",
                transferId,
                actor
        );

        // 10. Записываем зачисление в финансовый журнал.

        movements.insert(
                UUID.randomUUID(),
                to,
                amount,
                "TRANSFER",
                "TRANSFER",
                transferId,
                actor
        );

        // 11. Возвращаем идентификатор перевода.
        // Spring зафиксирует транзакцию после выхода из метода.

        return transferId;
    }
}