package kg.chairx.payment.application;

import kg.chairx.finance.application.FinancePostingService;
import jakarta.validation.Valid;
import kg.chairx.audit.AuditService;
import kg.chairx.delivery.persistence.DeliveryRepository;
import kg.chairx.payment.api.CancelPaymentRequest;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.api.PaymentResponse;
import kg.chairx.payment.domain.Payment;
import kg.chairx.payment.domain.PaymentStatus;
import kg.chairx.payment.domain.PaymentChannel;
import kg.chairx.payment.persistence.PaymentRepository;
import kg.chairx.refund.persistence.RefundRepository;
import kg.chairx.sale.application.SaleNotFoundException;
import kg.chairx.sale.domain.Sale;
import kg.chairx.sale.domain.SaleStatus;
import kg.chairx.sale.persistence.SaleRepository;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Validated
@Transactional(readOnly = true)
public class PaymentService {

    private final FinancePostingService finance;
    private final PaymentRepository repository;
    private final SaleRepository sales;
    private final RefundRepository refunds;
    private final DeliveryRepository deliveries;
    private final AuditService audit;

    public PaymentService(
            FinancePostingService finance,
            PaymentRepository repository,
            SaleRepository sales,
            RefundRepository refunds,
            DeliveryRepository deliveries,
            AuditService audit
    ) {
        this.finance = finance;
        this.repository = repository;
        this.sales = sales;
        this.refunds = refunds;
        this.deliveries = deliveries;
        this.audit = audit;
    }

    @Transactional
    public PaymentResponse create(
            @Valid CreatePaymentRequest request
    ) {
        // Блокируем продажу для синхронизации операций оплаты.
        Sale sale = sales.lock(request.saleId())
                .orElseThrow(SaleNotFoundException::new);

        if (sale.status() == SaleStatus.CANCELLED) {
            throw rule(
                    "SALE_CANCELLED",
                    "Нельзя зарегистрировать оплату для отменённой продажи"
            );
        }

        if (sale.status() != SaleStatus.CONFIRMED
                && sale.status() != SaleStatus.FULFILLED) {
            throw rule(
                    "INVALID_SALE_STATUS",
                    "Нельзя зарегистрировать оплату для продажи "
                            + "в текущем состоянии"
            );
        }

        // A delivery sale must have a persisted delivery and its price before payment.
        // Locking the sale serializes this decision with sale-related operations.
        var linkedDelivery = deliveries.findBySaleId(sale.id());
        if (sale.fulfillmentType() != kg.chairx.sale.domain.FulfillmentType.SELF_PICKUP
                && (linkedDelivery.isEmpty() || linkedDelivery.get().cancelled())) {
            throw rule("DELIVERY_REQUIRED_BEFORE_PAYMENT",
                    "Сначала оформите действующую доставку, чтобы рассчитать полную сумму оплаты");
        }

        // Return of a failed delivery also takes the Sale row lock.
        // This check therefore observes the serialized business outcome.
        if (deliveries.findBySaleId(sale.id())
                .filter(delivery -> delivery.failed() && delivery.returnedToWarehouse())
                .isPresent()) {
            throw rule(
                    "FAILED_DELIVERY_ALREADY_RETURNED",
                    "Нельзя принимать оплату за неуспешную доставку после возврата товара"
            );
        }

        if (repository.findActiveBySale(sale.id()).isPresent()) {
            throw rule(
                    "SALE_ALREADY_PAID",
                    "Для продажи уже зарегистрирована оплата"
            );
        }

        PaymentChannel channel = request.channel() == null
                ? PaymentChannel.defaultFor(request.method()) : request.channel();
        if (!channel.compatibleWith(request.method())) {
            throw rule("PAYMENT_CHANNEL_MISMATCH",
                    "Канал оплаты не соответствует выбранному финансовому счёту");
        }

        // Bank installment is registered only after actual bank settlement,
        // exactly like any other successful TRANSFER.
        Payment payment = new Payment(
                UUID.randomUUID(),
                sale.id(),
                sale.total().add(linkedDelivery.map(d -> d.deliveryCost())
                        .orElse(java.math.BigDecimal.ZERO)),
                request.method(),
                PaymentStatus.PAID,
                request.reference(),
                request.comment(),
                actor(),
                Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                null,
                null,
                null,
                channel
        );

        repository.insert(payment);
        if (payment.amount().signum() > 0) {
            finance.post(payment.method().name(), payment.amount(), "SALE_PAYMENT", "PAYMENT", payment.id(), payment.paidBy());
        }

        PaymentResponse result =
                PaymentMapper.toResponse(payment);

        audit.record(
                "PAYMENT",
                payment.id(),
                "PAID",
                null,
                result
        );

        return result;
    }

    public PaymentResponse get(UUID paymentId) {
        return PaymentMapper.toResponse(
                repository.find(paymentId)
                        .orElseThrow(PaymentNotFoundException::new)
        );
    }

    public List<PaymentResponse> getBySale(UUID saleId) {
        return repository.findBySale(saleId)
                .stream()
                .map(PaymentMapper::toResponse)
                .toList();
    }

    public PaymentResponse getActiveBySale(UUID saleId) {
        return PaymentMapper.toResponse(
                repository.findActiveBySale(saleId)
                        .orElseThrow(PaymentNotFoundException::new)
        );
    }

    @Transactional
    public PaymentResponse cancel(
            UUID paymentId,
            @Valid CancelPaymentRequest request
    ) {
        Payment payment = repository.lock(paymentId)
                .orElseThrow(PaymentNotFoundException::new);

        if (payment.cancelled()) {
            return PaymentMapper.toResponse(payment);
        }

        if (!payment.paid()) {
            throw rule(
                    "INVALID_PAYMENT_STATUS",
                    "Оплату нельзя аннулировать в текущем состоянии"
            );
        }

        // A fulfilled pickup has already removed physical stock and posted FIFO cost.
        // Reversing only its payment would leave an unpaid completed sale.
        // Use the return/refund workflow for a completed handover.
        var paidSale = sales.find(payment.saleId())
                .orElseThrow(SaleNotFoundException::new);
        if (paidSale.fulfillmentType() == kg.chairx.sale.domain.FulfillmentType.SELF_PICKUP
                && paidSale.status() == SaleStatus.FULFILLED) {
            throw rule("FULFILLED_PICKUP_REQUIRES_RETURN",
                    "Выданный самовывоз нельзя аннулировать как ошибочную оплату. Оформите возврат товара и денег.");
        }

        if (refunds.existsBySale(payment.saleId())) {
            throw rule(
                    "PAYMENT_HAS_REFUNDS",
                    "Нельзя аннулировать оплату, "
                            + "по которой уже был выполнен возврат денег"
            );
        }



        PaymentResponse before =
                PaymentMapper.toResponse(payment);

        String actor = actor();
        Instant cancelledAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);

        Payment cancelled = payment.cancel(
                actor,
                cancelledAt,
                request.reason()
        );

        if (payment.amount().signum() > 0) {
            finance.reversePayment(payment.method().name(), payment.amount(), payment.id(), actor);
        }
        repository.cancel(
                payment.id(),
                cancelled.cancelledBy(),
                cancelled.cancelledAt(),
                cancelled.cancellationReason()
        );

        PaymentResponse after =
                PaymentMapper.toResponse(cancelled);

        audit.record(
                "PAYMENT",
                payment.id(),
                "CANCELLED",
                before,
                after
        );

        return after;
    }

    private String actor() {
        return SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();
    }

    private PaymentRuleViolationException rule(
            String code,
            String message
    ) {
        return new PaymentRuleViolationException(code, message);
    }
}