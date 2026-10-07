package kg.chairx.payment.application;

import jakarta.validation.Valid;
import kg.chairx.audit.AuditService;
import kg.chairx.payment.api.CancelPaymentRequest;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.api.PaymentResponse;
import kg.chairx.payment.domain.Payment;
import kg.chairx.payment.domain.PaymentStatus;
import kg.chairx.payment.persistence.PaymentRepository;
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

    private final PaymentRepository repository;
    private final SaleRepository sales;
    private final AuditService audit;

    public PaymentService(
            PaymentRepository repository,
            SaleRepository sales,
            AuditService audit
    ) {
        this.repository = repository;
        this.sales = sales;
        this.audit = audit;
    }

    @Transactional
    public PaymentResponse create(
            @Valid CreatePaymentRequest request
    ) {
        Sale sale = sales.lock(request.saleId())
                .orElseThrow(
                        SaleNotFoundException::new
                );

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
                    "Нельзя зарегистрировать оплату для продажи в текущем состоянии"
            );
        }

        if (repository.findActiveBySale(
                sale.id()
        ).isPresent()) {
            throw rule(
                    "SALE_ALREADY_PAID",
                    "Для продажи уже зарегистрирована оплата"
            );
        }

        Payment payment = new Payment(
                UUID.randomUUID(),
                sale.id(),
                sale.total(),
                request.method(),
                PaymentStatus.PAID,
                request.reference(),
                request.comment(),
                actor(),
                Instant.now(),
                null,
                null,
                null
        );

        repository.insert(payment);

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

    public PaymentResponse get(
            UUID paymentId
    ) {
        return PaymentMapper.toResponse(
                repository.find(paymentId)
                        .orElseThrow(
                                PaymentNotFoundException::new
                        )
        );
    }

    public List<PaymentResponse> getBySale(
            UUID saleId
    ) {
        return repository.findBySale(saleId)
                .stream()
                .map(PaymentMapper::toResponse)
                .toList();
    }

    public PaymentResponse getActiveBySale(
            UUID saleId
    ) {
        return PaymentMapper.toResponse(
                repository.findActiveBySale(saleId)
                        .orElseThrow(
                                PaymentNotFoundException::new
                        )
        );
    }

    @Transactional
    public PaymentResponse cancel(
            UUID paymentId,
            @Valid CancelPaymentRequest request
    ) {
        Payment payment = repository.lock(paymentId)
                .orElseThrow(
                        PaymentNotFoundException::new
                );

        if (payment.cancelled()) {
            return PaymentMapper.toResponse(payment);
        }

        if (!payment.paid()) {
            throw rule(
                    "INVALID_PAYMENT_STATUS",
                    "Оплату нельзя аннулировать в текущем состоянии"
            );
        }

        PaymentResponse before =
                PaymentMapper.toResponse(payment);

        String actor = actor();
        Instant cancelledAt = Instant.now();

        Payment cancelled = payment.cancel(
                actor,
                cancelledAt,
                request.reason()
        );

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
        return new PaymentRuleViolationException(
                code,
                message
        );
    }
}