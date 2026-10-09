package kg.chairx.finance.domain;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException() {
        super("Недостаточно средств на счёте");
    }
}