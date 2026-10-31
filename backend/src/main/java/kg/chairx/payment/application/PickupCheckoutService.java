package kg.chairx.payment.application;

import jakarta.validation.Valid;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.api.PaymentResponse;
import kg.chairx.sale.application.SaleService;
import kg.chairx.sale.domain.FulfillmentType;
import kg.chairx.sale.domain.SaleStatus;
import kg.chairx.sale.persistence.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomic cashier checkout: payment, finance posting, FIFO stock out and sale completion. */
@Service
public class PickupCheckoutService {
    private final PaymentService payments;
    private final SaleService sales;
    private final SaleRepository saleRepository;

    public PickupCheckoutService(PaymentService payments, SaleService sales,
                                 SaleRepository saleRepository) {
        this.payments = payments;
        this.sales = sales;
        this.saleRepository = saleRepository;
    }

    @Transactional
    public PaymentResponse checkout(@Valid CreatePaymentRequest request) {
        // Daily closing and other completion operations acquire this gate before
        // locking a sale. Preserve the same lock order to avoid deadlocks.
        saleRepository.acquireCompletionGate();
        var sale = sales.get(request.saleId());
        if (sale.status() == SaleStatus.CANCELLED) {
            throw new PaymentRuleViolationException("SALE_CANCELLED",
                    "Нельзя зарегистрировать оплату для отменённой продажи");
        }
        if (sale.fulfillmentType() != FulfillmentType.SELF_PICKUP) {
            throw new PaymentRuleViolationException("INVALID_FULFILLMENT_TYPE",
                    "Операция оплаты с выдачей доступна только для самовывоза");
        }
        if (sale.status() == SaleStatus.FULFILLED) {
            throw new PaymentRuleViolationException("SALE_ALREADY_PAID",
                    "Для завершённой продажи уже зарегистрирована оплата");
        }
        if (sale.status() != SaleStatus.CONFIRMED) {
            throw new PaymentRuleViolationException("INVALID_SALE_STATUS",
                    "Оплата с выдачей доступна только подтверждённому самовывозу");
        }
        PaymentResponse payment = payments.create(request);
        // Both services join this transaction. An inventory failure rolls back
        // the payment and ledger posting; successful pickup is immediately FULFILLED.
        sales.fulfill(request.saleId());
        return payment;
    }
}
