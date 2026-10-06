package kg.chairx.purchase.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kg.chairx.audit.AuditService;
import kg.chairx.product.application.ProductVariantService;
import kg.chairx.purchase.api.*;
import kg.chairx.purchase.domain.*;
import kg.chairx.purchase.persistence.PurchaseRepository;
import kg.chairx.supplier.application.SupplierService;
import kg.chairx.warehouse.application.WarehouseService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.json.JsonMapper;

import static kg.chairx.purchase.domain.PurchaseStatus.*;

@Service
@Validated
@Transactional(readOnly = true)
public class PurchaseService {
    private final PurchaseRepository repository;
    private final SupplierService suppliers;
    private final ProductVariantService variants;
    private final WarehouseService warehouses;
    private final AuditService audit;
    private final JsonMapper mapper;

    public PurchaseService(PurchaseRepository repository, SupplierService suppliers, ProductVariantService variants,
            WarehouseService warehouses, AuditService audit, JsonMapper mapper) {
        this.repository=repository;
        this.suppliers=suppliers;
        this.variants=variants;
        this.warehouses=warehouses;
        this.audit=audit;
        this.mapper=mapper;
    }

    @Transactional
    public PurchaseResponse create(@Valid CreatePurchaseRequest request) {
        validateDraftReferences(request.supplierId(),request.items());
        UUID id=UUID.randomUUID();
        repository.insert(id,request.supplierId(),request.cargoCost(),normalize(request.comment()),actor());
        repository.replaceDraftItems(id,request.items());
        allocateCargo(id,request.cargoCost());
        var result=get(id);
        audit.record("PURCHASE",id,"CREATED",null,result);
        return result;
    }

    @Transactional
    public PurchaseResponse update(UUID id, @Valid UpdatePurchaseRequest request) {
        var purchase=lock(id);
        requireStatus(purchase,DRAFT);
        validateDraftReferences(request.supplierId(),request.items());
        var before=response(purchase);
        repository.updateDraft(id,request.supplierId(),request.cargoCost(),normalize(request.comment()));
        repository.replaceDraftItems(id,request.items());
        allocateCargo(id,request.cargoCost());
        var after=get(id);
        audit.record("PURCHASE",id,"UPDATED",before,after);
        return after;
    }

    @Transactional
    public PurchaseResponse confirm(UUID id) {
        var purchase=lock(id);
        if (purchase.status()==CONFIRMED) { return response(purchase); }
        requireStatus(purchase,DRAFT);
        var items=repository.items(id);
        if (items.isEmpty()) { throw rule("EMPTY_PURCHASE","В закупке нет позиций"); }
        validateDraftReferences(purchase.supplierId(),items.stream()
                .map(item -> new PurchaseItemRequest(item.productVariantId(),item.orderedQuantity(),item.purchaseUnitCost())).toList());
        var before=response(purchase);
        repository.setStatus(id,CONFIRMED);
        var after=get(id);
        audit.record("PURCHASE",id,"CONFIRMED",before,after);
        return after;
    }

    @Transactional
    public PurchaseResponse cancel(UUID id) {
        var purchase=lock(id);
        if (purchase.status()==CANCELLED) { return response(purchase); }
        requireStatus(purchase,DRAFT,CONFIRMED);
        if (repository.items(id).stream().anyMatch(item -> item.receivedQuantity()!=0)) {
            throw rule("PURCHASE_ALREADY_RECEIVED","Закупку с поступлениями нельзя отменить");
        }
        var before=response(purchase);
        repository.setStatus(id,CANCELLED);
        var after=get(id);
        audit.record("PURCHASE",id,"CANCELLED",before,after);
        return after;
    }

    @Transactional
    public PurchaseResponse setCargo(UUID id, @Valid SetCargoCostRequest request) {
        var purchase=lock(id);
        requireStatus(purchase,DRAFT,CONFIRMED);
        if (purchase.costsLockedAt()!=null) { throw rule("PURCHASE_COSTS_LOCKED","Себестоимость уже зафиксирована поступлением"); }
        var before=response(purchase);
        if (purchase.cargoCost()!=null && purchase.cargoCost().compareTo(request.cargoCost())==0) { return before; }
        repository.setCargo(id,request.cargoCost());
        allocateCargo(id,request.cargoCost());
        var after=get(id);
        audit.record("PURCHASE",id,"CARGO_CHANGED",before,after);
        return after;
    }

    public PurchaseResponse get(UUID id) {
        return response(repository.find(id).orElseThrow(PurchaseNotFoundException::new));
    }

    public PurchasePageResponse list(@Min(0) int page,@Min(1) @Max(100) int size) {
        return new PurchasePageResponse(repository.list(page,size).stream().map(p ->
                new PurchaseSummary(p.id(),p.supplierId(),p.status(),p.cargoCost(),p.createdAt())).toList(),
                page,size,repository.count());
    }

    public PurchaseReceiptResponse getReceipt(UUID purchaseId,UUID receiptId) {
        return receiptResponse(repository.receipt(purchaseId,receiptId).orElseThrow(PurchaseNotFoundException::new));
    }

    public PurchaseReceiptPageResponse receipts(UUID purchaseId,@Min(0) int page,@Min(1) @Max(100) int size) {
        repository.find(purchaseId).orElseThrow(PurchaseNotFoundException::new);
        return new PurchaseReceiptPageResponse(repository.listReceipts(purchaseId,page,size).stream()
                .map(r -> new PurchaseReceiptSummary(r.id(),r.warehouseId(),r.postedAt(),r.createdBy())).toList(),
                page,size,repository.receiptCount(purchaseId));
    }

    /** Internal half of ReceivePurchase. Must share its outer transaction with every Inventory call. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ReceiptPosting prepareReceipt(UUID purchaseId,@Valid CreatePurchaseReceiptRequest request) {
        var purchase=lock(purchaseId);
        var seen=new HashSet<UUID>();
        for (var line:request.items()) {
            if (!seen.add(line.purchaseItemId())) { throw rule("DUPLICATE_RECEIPT_ITEM","Позиция закупки указана в поступлении повторно"); }
        }
        String fingerprint=fingerprint(request);
        var existing=repository.receiptByKey(purchaseId,request.idempotencyKey());
        if (existing.isPresent()) {
            if (!existing.get().requestFingerprint().equals(fingerprint)) {
                throw rule("RECEIPT_IDEMPOTENCY_CONFLICT","Ключ поступления уже использован с другими данными");
            }
            return new ReceiptPosting(receiptResponse(existing.get()),true);
        }
        requireStatus(purchase,CONFIRMED,PARTIALLY_RECEIVED);
        if (purchase.cargoCost()==null) { throw rule("CARGO_COST_REQUIRED","Укажите окончательную стоимость карго до первого поступления"); }
        if (!warehouses.get(request.warehouseId()).active()) { throw rule("WAREHOUSE_INACTIVE","Нельзя принять товар на неактивный склад"); }
        var items=repository.items(purchaseId);
        Map<UUID,PurchaseItem> byId=items.stream().collect(Collectors.toMap(PurchaseItem::id,Function.identity()));
        for (var line:request.items()) {
            var item=byId.get(line.purchaseItemId());
            if (item==null) { throw rule("INVALID_RECEIPT_ITEM","Позиция не принадлежит этой закупке"); }
            if (line.quantity()>item.orderedQuantity()-item.receivedQuantity()) {
                throw rule("PURCHASE_QUANTITY_EXCEEDED","Количество поступления превышает остаток по позиции закупки");
            }
        }
        var before=response(purchase);
        UUID receiptId=UUID.randomUUID();
        repository.insertReceipt(receiptId,purchaseId,request.warehouseId(),request.idempotencyKey(),fingerprint,
                normalize(request.comment()),actor());
        for (var line:request.items()) {
            var item=byId.get(line.purchaseItemId());
            BigDecimal cargo=CargoAllocation.receiptShare(item.allocatedCargoCost(),item.orderedQuantity(),item.receivedQuantity(),line.quantity());
            BigDecimal total=item.purchaseUnitCost().multiply(BigDecimal.valueOf(line.quantity())).add(cargo);
            repository.insertReceiptItem(UUID.randomUUID(),purchaseId,receiptId,item.id(),line.quantity(),cargo,total);
        }
        boolean complete=repository.items(purchaseId).stream().allMatch(item -> item.receivedQuantity()==item.orderedQuantity());
        repository.finishReceipt(purchaseId,complete ? RECEIVED : PARTIALLY_RECEIVED);
        var receipt=getReceipt(purchaseId,receiptId);
        audit.record("PURCHASE",purchaseId,"RECEIPT_POSTED",before,get(purchaseId));
        audit.record("PURCHASE_RECEIPT",receiptId,"POSTED",null,receipt);
        return new ReceiptPosting(receipt,false);
    }

    private void allocateCargo(UUID purchaseId,BigDecimal cargo) {
        var items=repository.items(purchaseId);
        if (cargo==null) {
            for (var item:items) { repository.setItemCosts(item.id(),null,null); }
            return;
        }
        var allocated=CargoAllocation.byQuantity(cargo,items.stream().map(PurchaseItem::orderedQuantity).toList());
        for (int i=0;i<items.size();i++) {
            var item=items.get(i);
            repository.setItemCosts(item.id(),allocated.get(i),CargoAllocation.unitCost(item.purchaseUnitCost(),allocated.get(i),item.orderedQuantity()));
        }
    }

    private void validateDraftReferences(UUID supplierId,List<PurchaseItemRequest> items) {
        if (!suppliers.get(supplierId).active()) { throw rule("SUPPLIER_INACTIVE","Выберите активного поставщика"); }
        for (var item:items) {
            if (!variants.get(item.productVariantId()).active()) { throw rule("PRODUCT_VARIANT_INACTIVE","Выберите активный вариант товара"); }
        }
    }

    private Purchase lock(UUID id) { return repository.lock(id).orElseThrow(PurchaseNotFoundException::new); }

    private void requireStatus(Purchase purchase,PurchaseStatus... allowed) {
        for (var status:allowed) { if (purchase.status()==status) { return; } }
        throw rule("INVALID_PURCHASE_STATUS","Действие недоступно в текущем состоянии закупки");
    }

    private PurchaseResponse response(Purchase purchase) {
        return new PurchaseResponse(purchase.id(),purchase.supplierId(),purchase.status(),purchase.cargoCost(),
                purchase.cargoAllocationMethod(),purchase.costsLockedAt(),purchase.confirmedAt(),purchase.comment(),purchase.createdBy(),
                purchase.createdAt(),purchase.updatedAt(),repository.items(purchase.id()).stream().map(i -> new PurchaseItemResponse(
                        i.id(),i.productVariantId(),i.lineNumber(),i.orderedQuantity(),i.receivedQuantity(),
                        i.purchaseUnitCost(),i.allocatedCargoCost(),i.finalUnitCost())).toList());
    }

    private PurchaseReceiptResponse receiptResponse(PurchaseReceipt receipt) {
        return new PurchaseReceiptResponse(receipt.id(),receipt.purchaseId(),receipt.warehouseId(),receipt.idempotencyKey(),
                receipt.postedAt(),receipt.createdBy(),receipt.comment(),receipt.items().stream().map(i ->
                        new PurchaseReceiptItemResponse(i.id(),i.purchaseItemId(),i.productVariantId(),i.quantity(),i.allocatedCargoCost(),i.totalCost())).toList());
    }

    private String fingerprint(CreatePurchaseReceiptRequest request) {
        var sorted=request.items().stream().sorted(Comparator.comparing(item -> item.purchaseItemId().toString())).toList();
        String json=mapper.writeValueAsString(new ReceiptContent(request.warehouseId(),sorted,normalize(request.comment())));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable",exception); }
    }

    private record ReceiptContent(UUID warehouseId,List<ReceiptItemRequest> items,String comment) { }
    private String actor() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    private String normalize(String value) { return value==null || value.isBlank() ? null : value.strip(); }
    private PurchaseRuleViolationException rule(String code,String message) { return new PurchaseRuleViolationException(code,message); }
}
