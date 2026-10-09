package kg.chairx.refund.application;

import kg.chairx.finance.application.FinancePostingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import kg.chairx.audit.AuditService;
import kg.chairx.exchange.infrastructure.ExchangeRepository;
import kg.chairx.payment.application.PaymentBalanceService;
import kg.chairx.payment.persistence.PaymentRepository;
import kg.chairx.refund.api.CreateRefundRequest;
import kg.chairx.refund.api.RefundResponse;
import kg.chairx.refund.domain.Refund;
import kg.chairx.refund.persistence.RefundRepository;
import kg.chairx.returning.persistence.ReturnRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@Validated
@Transactional(readOnly = true)
public class RefundService {

    private final FinancePostingService finance;
    private final RefundRepository repository;
    private final PaymentRepository payments;
    private final PaymentBalanceService paymentBalanceService;
    private final ReturnRepository returns;
    private final ExchangeRepository exchanges;
    private final AuditService audit;

    public RefundService(
            FinancePostingService finance,
            RefundRepository repository,
            PaymentRepository payments,
            PaymentBalanceService paymentBalanceService,
            ReturnRepository returns,
            ExchangeRepository exchanges,
            AuditService audit
    ) {
        this.finance = finance;
        this.repository = repository;
        this.payments = payments;
        this.paymentBalanceService = paymentBalanceService;
        this.returns = returns;
        this.exchanges = exchanges;
        this.audit = audit;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RefundResponse create(
            @NotNull @Valid CreateRefundRequest request
    ) {
        String fingerprint = fingerprint(request);

        Refund existing = repository
                .findByIdempotencyKey(request.idempotencyKey())
                .orElse(null);

        if (existing != null) {
            return replayOrReject(existing, fingerprint);
        }

        payments.lockActiveBySale(request.saleId())
                .orElseThrow(() -> rule(
                        "SALE_NOT_PAID",
                        "Для продажи нет активной оплаты"
                ));

        existing = repository
                .findByIdempotencyKey(request.idempotencyKey())
                .orElse(null);

        if (existing != null) {
            return replayOrReject(existing, fingerprint);
        }

        if (request.returnId() != null) {
            var saleReturn = returns.find(request.returnId())
                    .orElseThrow(() -> rule(
                            "RETURN_NOT_FOUND",
                            "Возврат товара не найден"
                    ));

            if (!saleReturn.saleId().equals(request.saleId())) {
                throw rule(
                        "RETURN_SALE_MISMATCH",
                        "Возврат товара относится к другой продаже"
                );
            }

            if (repository.existsByReturn(request.returnId())) {
                throw rule(
                        "RETURN_ALREADY_REFUNDED",
                        "По этому возврату товара деньги уже возвращены"
                );
            }

            if (exchanges.existsByReturn(request.returnId())) {
                throw rule(
                        "RETURN_ALREADY_EXCHANGED",
                        "Этот возврат товара уже использован для обмена"
                );
            }
        }

        BigDecimal remaining =
                paymentBalanceService.availableBalance(request.saleId());

        if (request.amount().compareTo(remaining) > 0) {
            throw rule(
                    "REFUND_AMOUNT_EXCEEDED",
                    "Сумма возврата превышает доступный остаток оплаты"
            );
        }

        Refund refund = new Refund(
                UUID.randomUUID(),
                request.saleId(),
                request.returnId(),
                request.amount(),
                request.method(),
                request.reason().trim(),
                normalize(request.reference()),
                normalize(request.comment()),
                actor(),
                Instant.now()
        );

        boolean inserted = repository.tryInsert(
                refund,
                request.idempotencyKey(),
                fingerprint
        );

        if (!inserted) {
            Refund concurrent = repository
                    .findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "Refund idempotency conflict without existing record"
                    ));

            return replayOrReject(concurrent, fingerprint);
        }

        finance.post(refund.method().name(), refund.amount().negate(), "CUSTOMER_REFUND", "REFUND", refund.id(), refund.refundedBy());
        RefundResponse response = toResponse(refund);

        audit.record(
                "REFUND",
                refund.id(),
                "REFUNDED",
                null,
                response
        );

        return response;
    }

    public RefundResponse get(UUID refundId) {
        return toResponse(
                repository.find(refundId)
                        .orElseThrow(() ->
                                new RefundNotFoundException(refundId)
                        )
        );
    }

    public List<RefundResponse> getBySale(UUID saleId) {
        return repository.findBySale(saleId)
                .stream()
                .map(RefundService::toResponse)
                .toList();
    }

    private RefundResponse replayOrReject(
            Refund existing,
            String fingerprint
    ) {
        String existingFingerprint = repository
                .requestFingerprint(existing.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Refund request fingerprint отсутствует"
                ));

        if (!existingFingerprint.equals(fingerprint)) {
            throw rule(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Ключ операции уже использован для другого возврата денег"
            );
        }

        return toResponse(existing);
    }

    private static RefundResponse toResponse(Refund refund) {
        return new RefundResponse(
                refund.id(),
                refund.saleId(),
                refund.returnId(),
                refund.amount(),
                refund.method(),
                refund.reason(),
                refund.reference(),
                refund.comment(),
                refund.refundedBy(),
                refund.refundedAt()
        );
    }

    private static String fingerprint(CreateRefundRequest request) {
        String canonical = String.join(
                "|",
                request.saleId().toString(),
                request.returnId() == null
                        ? ""
                        : request.returnId().toString(),
                canonicalAmount(request.amount()),
                request.method().name(),
                canonicalText(request.reason()),
                canonicalText(request.reference()),
                canonicalText(request.comment())
        );

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    digest.digest(
                            canonical.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 недоступен",
                    exception
            );
        }
    }

    private static String canonicalAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private static String canonicalText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty() ? null : normalized;
    }

    private String actor() {
        return SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();
    }

    private RefundRuleViolationException rule(
            String code,
            String message
    ) {
        return new RefundRuleViolationException(code, message);
    }
}