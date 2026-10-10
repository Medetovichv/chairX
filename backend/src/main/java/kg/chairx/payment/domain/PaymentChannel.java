package kg.chairx.payment.domain;

/** Business-facing channel. Financial settlement still uses CASH or BANK. */
public enum PaymentChannel {
    CASH, BANK_TRANSFER, MBANK, BANK_INSTALLMENT;

    public boolean compatibleWith(PaymentMethod method) {
        return this == CASH ? method == PaymentMethod.CASH : method == PaymentMethod.TRANSFER;
    }

    public static PaymentChannel defaultFor(PaymentMethod method) {
        return method == PaymentMethod.CASH ? CASH : BANK_TRANSFER;
    }
}
