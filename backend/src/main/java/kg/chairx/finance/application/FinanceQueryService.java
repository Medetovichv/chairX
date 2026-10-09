package kg.chairx.finance.application;

import kg.chairx.finance.api.FinanceAccountResponse;
import kg.chairx.finance.persistence.FinanceAccountRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FinanceQueryService {

    private final FinanceAccountRepository accounts;

    public FinanceQueryService(FinanceAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<FinanceAccountResponse> getAccounts() {
        return accounts.findAllAccounts()
                .stream()
                .map(snapshot -> new FinanceAccountResponse(
                        snapshot.account().name(),
                        snapshot.balance(),
                        snapshot.initialized()
                ))
                .toList();
    }
}