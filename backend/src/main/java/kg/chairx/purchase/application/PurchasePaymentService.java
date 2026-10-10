package kg.chairx.purchase.application;

import kg.chairx.finance.application.FinancePostingService;
import kg.chairx.purchase.api.*;
import kg.chairx.purchase.domain.*;
import kg.chairx.purchase.persistence.PurchasePaymentRepository;
import kg.chairx.purchase.persistence.PurchaseRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
public class PurchasePaymentService {
    private final PurchaseRepository purchases;
    private final PurchasePaymentRepository payments;
    private final FinancePostingService finance;

    public PurchasePaymentService(
            PurchaseRepository purchases, PurchasePaymentRepository payments,
            FinancePostingService finance) {
        this.purchases=purchases;
        this.payments=payments;
        this.finance=finance;
    }

    @Transactional
    public PurchasePaymentResponse create(UUID purchaseId, CreatePurchasePaymentRequest request) {
        if (purchaseId==null || request==null || request.idempotencyKey()==null
                || request.paymentKind()==null || request.account()==null
                || request.amount()==null || request.amount().signum()<=0
                || request.amount().stripTrailingZeros().scale()>0
                || request.reference()!=null && request.reference().strip().length()>200
                || request.comment()!=null && request.comment().strip().length()>1000) {
            throw rule("INVALID_PURCHASE_PAYMENT","Некорректные параметры выплаты закупки");
        }

        var amount=request.amount().setScale(0);
        String reference=normalize(request.reference());
        String comment=normalize(request.comment());
        String fingerprint=fingerprint(request,amount,reference,comment);
        // A committed retry remains safe after purchase cancellation/closing.
        var already=payments.findByKey(request.idempotencyKey());
        if(already.isPresent()) return replay(already.get(),purchaseId,fingerprint);

        // All writes to purchase payments, cancellation and cargo cost changes
        // serialize on the purchase row. No additional account lock inversion.
        var purchase=purchases.lock(purchaseId).orElseThrow(PurchaseNotFoundException::new);
        already=payments.findByKey(request.idempotencyKey());
        if(already.isPresent()) return replay(already.get(),purchaseId,fingerprint);

        if(purchase.status()!=PurchaseStatus.CONFIRMED
                && purchase.status()!=PurchaseStatus.PARTIALLY_RECEIVED
                && purchase.status()!=PurchaseStatus.RECEIVED) {
            throw rule("INVALID_PURCHASE_STATUS","Оплата доступна после подтверждения закупки");
        }

        BigDecimal total=request.paymentKind()==PurchasePaymentKind.SUPPLIER
                ? purchases.items(purchaseId).stream()
                    .map(item -> item.purchaseUnitCost()
                        .multiply(BigDecimal.valueOf(item.orderedQuantity())))
                    .reduce(BigDecimal.ZERO,BigDecimal::add)
                : purchase.cargoCost();
        if(total==null) throw rule("PURCHASE_CARGO_UNDEFINED",
                "Нужно указать стоимость карго до выплаты за карго");
        BigDecimal previouslyPaid=payments.total(purchaseId,request.paymentKind());
        if(previouslyPaid.add(amount).compareTo(total)>0)
            throw rule("PURCHASE_PAYMENT_EXCEEDS_TOTAL",
                    "Суммарные выплаты превышают стоимость выбранной категории закупки");

        PurchasePayment operation=new PurchasePayment(
                UUID.randomUUID(),purchaseId,request.paymentKind(),request.account(),
                amount,request.idempotencyKey(),fingerprint,reference,comment,actor(),null);
        if(!payments.insert(operation)) {
            var committed=payments.findByKey(request.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "Ключ выплаты существует, но платёж не найден"));
            return replay(committed,purchaseId,fingerprint);
        }
        finance.post(request.account().name(),amount.negate(),
                "PURCHASE_PAYMENT","PURCHASE_PAYMENT",operation.id(),operation.createdBy());
        var saved=payments.findByKey(request.idempotencyKey()).orElseThrow();
        return PurchasePaymentResponse.from(saved);
    }

    @Transactional(readOnly=true)
    public PurchasePaymentSummaryResponse list(UUID purchaseId) {
        var purchase=purchases.find(purchaseId).orElseThrow(PurchaseNotFoundException::new);
        BigDecimal supplier=purchases.items(purchaseId).stream()
                .map(item -> item.purchaseUnitCost()
                        .multiply(BigDecimal.valueOf(item.orderedQuantity())))
                .reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal paidSupplier=payments.total(purchaseId,PurchasePaymentKind.SUPPLIER);
        BigDecimal paidCargo=payments.total(purchaseId,PurchasePaymentKind.CARGO);
        var cargo=purchase.cargoCost();
        return new PurchasePaymentSummaryResponse(purchaseId,supplier,cargo,
                paidSupplier,paidCargo,supplier.subtract(paidSupplier),
                cargo==null?null:cargo.subtract(paidCargo),
                payments.findByPurchase(purchaseId).stream()
                        .map(PurchasePaymentResponse::from).toList());
    }

    private PurchasePaymentResponse replay(PurchasePayment prior,UUID purchaseId,String fingerprint) {
        if(!prior.purchaseId().equals(purchaseId)
                || !prior.requestFingerprint().equals(fingerprint))
            throw rule("PURCHASE_PAYMENT_IDEMPOTENCY_CONFLICT",
                    "Ключ выплаты уже использован с другими параметрами");
        return PurchasePaymentResponse.from(prior);
    }

    private static String fingerprint(CreatePurchasePaymentRequest r,BigDecimal amount,
                                      String reference,String comment) {
        StringBuilder canonical=new StringBuilder();
        for(String value:new String[]{r.paymentKind().name(),r.account().name(),
                amount.toPlainString(),reference,comment}) {
            canonical.append(value==null?"-1:":value.length()+":"+value);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch(NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 недоступен",e); }
    }

    private static String normalize(String text) {
        return text==null || text.isBlank()?null:text.strip();
    }
    private static String actor() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
    private static PurchaseRuleViolationException rule(String code,String msg) {
        return new PurchaseRuleViolationException(code,msg);
    }
}
