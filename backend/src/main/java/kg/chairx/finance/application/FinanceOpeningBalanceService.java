package kg.chairx.finance.application;

import kg.chairx.finance.domain.FinanceAccount;
import kg.chairx.finance.persistence.FinanceAccountRepository;
import kg.chairx.finance.persistence.FinanceMovementRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class FinanceOpeningBalanceService {

    private final FinanceAccountRepository accounts;
    private final FinanceMovementRepository movements;

    public FinanceOpeningBalanceService(
            FinanceAccountRepository accounts,
            FinanceMovementRepository movements
    ) {
        this.accounts = accounts;
        this.movements = movements;
    }

    @Transactional
    public void initialize(
            FinanceAccount account,
            BigDecimal amount,
            String actor
    ) {
        if (account == null
                || amount == null
                || amount.signum() < 0
                || amount.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "Некорректный начальный остаток"
            );
        }

        if (actor == null
                || actor.isBlank()
                || actor.length() > 200) {
            throw new IllegalArgumentException(
                    "Некорректный сотрудник"
            );
        }

        // Блокировка защищает от одновременной инициализации.
        BigDecimal currentBalance = accounts.lockBalance(account);

        if (accounts.isOpeningBalanceInitialized(account)) {
            throw new FinanceConflictException(
                    "Начальный остаток уже зарегистрирован"
            );
        }

        if (currentBalance.signum() != 0) {
            throw new FinanceConflictException(
                    "Нельзя установить начальный остаток: счёт уже содержит средства"
            );
        }

        UUID sourceId = UUID.nameUUIDFromBytes(
                ("CHAIRX_OPENING_BALANCE_" + account.name())
                        .getBytes(StandardCharsets.UTF_8)
        );

        if (amount.signum() > 0) {
            accounts.changeBalance(account, amount);

            movements.insert(
                    UUID.randomUUID(),
                    account,
                    amount,
                    "OPENING_BALANCE",
                    "OPENING_BALANCE",
                    sourceId,
                    actor
            );
        }

        // Выполняется даже при нулевом остатке.
        accounts.markOpeningBalanceInitialized(account);
    }
}