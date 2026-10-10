package kg.chairx.finance.application;

import kg.chairx.finance.domain.FinanceAccount;
import kg.chairx.finance.persistence.FinanceAccountRepository;
import kg.chairx.finance.persistence.FinanceMovementRepository;
import org.springframework.stereotype.Service;
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
    public FinancePostingService(FinanceAccountRepository accounts, FinanceMovementRepository movements) {
        this.accounts = accounts;
        this.movements = movements;
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

    public void reversePayment(String method, BigDecimal amount, UUID paymentId, String actor) {
        // Older documents were not posted. Never debit their amount automatically.
        if (!movements.exists("PAYMENT", paymentId, account(method))) {
            throw new FinancePostingException("Историческая оплата без проводки требует финансовой сверки");
        }
        post(method, amount.negate(), "PAYMENT_REVERSAL", "PAYMENT_REVERSAL", paymentId, actor);
    }
}
