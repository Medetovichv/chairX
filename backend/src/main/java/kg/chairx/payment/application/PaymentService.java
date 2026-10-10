package kg.chairx.payment.application;

import kg.chairx.finance.application.FinancePostingService;
import jakarta.validation.Valid;
import kg.chairx.audit.AuditService;
import kg.chairx.exchange.infrastructure.ExchangeRepository;
import kg.chairx.payment.api.CancelPaymentRequest;
import kg.chairx.payment.api.CreatePaymentRequest;
import kg.chairx.payment.api.PaymentResponse;
import kg.chairx.payment.domain.Payment;
import kg.chairx.payment.domain.PaymentStatus;
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
    private final ExchangeRepository exchanges;
    private final AuditService audit;

    public PaymentService(
            FinancePostingService finance,
            PaymentRepository repository,
            SaleRepository sales,
            RefundRepository refunds,
            ExchangeRepository exchanges,
            AuditService audit
    ) {
        this.finance = finance;
        this.repository = repository;
        this.sales = sales;
        this.refunds = refunds;
        this.exchanges = exchanges;
        this.audit = audit;
    }

    @Transactional
    public PaymentResponse create(
            @Valid CreatePaymentRequest request
    ) {
        // Блокируем продажу для синхронизации операций оплаты.
        Sale sale = sales.lock(request.saleId())
                .orElseThrow(SaleNotFoundException::new);

        /*
         * Новая продажа, созданная через Exchange,
         * уже имеет финансовый зачёт стоимости
         * возвращённого товара.
         *
         * Доплата или возврат разницы должны проходить
         * исключительно через ExchangeSettlementService.
         *
         * Обычная оплата полной стоимости запрещена.
         */
        if (exchanges.existsByNewSale(sale.id())) {
            throw rule(
                    "EXCHANGE_SALE_PAYMENT_FORBIDDEN",
                    "Эта продажа создана через обмен. "
                            + "Доплату необходимо зарегистрировать "
                            + "через финансовые расчёты Exchange"
            );
        }

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

        if (repository.findActiveBySale(sale.id()).isPresent()) {
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

        if (refunds.existsBySale(payment.saleId())) {
            throw rule(
                    "PAYMENT_HAS_REFUNDS",
                    "Нельзя аннулировать оплату, "
                            + "по которой уже был выполнен возврат денег"
            );
        }

        if (exchanges.existsByOriginalSale(payment.saleId())) {
            throw rule(
                    "PAYMENT_HAS_EXCHANGES",
                    "Нельзя аннулировать оплату, "
                            + "использованную при обмене"
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