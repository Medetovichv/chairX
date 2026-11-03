package kg.chairx.sale.application;

import jakarta.validation.Valid;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.application.PaymentService;
import kg.chairx.sale.api.SaleResponse;
import kg.chairx.sale.domain.FulfillmentType;
import kg.chairx.sale.domain.SaleStatus;
import kg.chairx.sale.persistence.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single explicit customer handover action for pickup orders.
 * Recording the full payment and issuing stock MUST commit or roll back together.
 * A standalone payment remains available when actual handover has not happened.
 */
@Service
public class PaidPickupService {
    private final SaleRepository repository;
    private final PaymentService payments;
    private final SaleService sales;

    public PaidPickupService(SaleRepository repository, PaymentService payments, SaleService sales) {
        this.repository = repository;
        this.payments = payments;
        this.sales = sales;
    }

    @Transactional
    public SaleResponse payAndHandOver(UUID saleId, @Valid CreatePaymentRequest request) {
        if(request == null || request.saleId() == null || !saleId.equals(request.saleId())) {
            throw new SaleRuleViolationException("SALE_MISMATCH",
                    "Платёж относится к другой продаже");
        }
        var sale = repository.lock(saleId).orElseThrow(SaleNotFoundException::new);
        if(sale.fulfillmentType() != FulfillmentType.SELF_PICKUP) {
            throw new SaleRuleViolationException("PICKUP_ONLY",
                    "Совместная оплата и выдача доступны только для самовывоза");
        }
        if(sale.status() != SaleStatus.CONFIRMED) {
            throw new SaleRuleViolationException("INVALID_SALE_STATUS",
                    "Выдача доступна только для подтверждённого заказа");
        }
        // PaymentService serializes with cancellations and refuses any active
        // payment. SaleService fulfills using existing FIFO inventory posting.
        // Both nested service calls join THIS transaction.
        payments.create(request);
        return sales.fulfill(saleId);
    }
}
