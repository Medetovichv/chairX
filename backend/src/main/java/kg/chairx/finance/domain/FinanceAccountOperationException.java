package kg.chairx.finance.domain;

public class FinanceAccountOperationException extends org.springframework.dao.InvalidDataAccessApiUsageException {
    public FinanceAccountOperationException(String message) { super(message); }
}
