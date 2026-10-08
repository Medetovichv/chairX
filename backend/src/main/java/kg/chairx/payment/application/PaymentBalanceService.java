package kg.chairx.payment.application;

import kg.chairx.exchange.infrastructure.ExchangeRepository;
import kg.chairx.payment.domain.Payment;
import kg.chairx.payment.persistence.PaymentRepository;
import kg.chairx.refund.persistence.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentBalanceService {

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final ExchangeRepository exchanges;

    public PaymentBalanceService(
            PaymentRepository payments,
            RefundRepository refunds,
            ExchangeRepository exchanges
    ) {
        this.payments = payments;
        this.refunds = refunds;
        this.exchanges = exchanges;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal availableBalance(UUID saleId) {
        Payment payment = payments.lockActiveBySale(saleId)
                .orElseThrow(() -> new IllegalStateException(
                        "Для продажи нет активной оплаты"
                ));

        BigDecimal refunded = refunds.refundedAmount(saleId);

        BigDecimal credited =
                exchanges.creditedAmountByOriginalSale(saleId);

        BigDecimal remaining = payment.amount()
                .subtract(refunded)
                .subtract(credited);

        if (remaining.signum() < 0) {
            throw new IllegalStateException(
                    "Нарушена финансовая целостность продажи"
            );
        }

        return remaining;
    }
}