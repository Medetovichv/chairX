package kg.chairx.sale.application;

import jakarta.validation.Valid;
import kg.chairx.exchange.application.ExchangeSaleGuard;
import kg.chairx.audit.AuditService;
import kg.chairx.customer.application.CustomerService;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.inventory.api.ChangeReservedStock;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.domain.StockMovementType;
import kg.chairx.product.application.ProductVariantService;
import kg.chairx.sale.api.CreateSaleItemRequest;
import kg.chairx.sale.api.CreateSaleRequest;
import kg.chairx.sale.application.SaleMapper;
import kg.chairx.sale.api.SaleResponse;
import kg.chairx.sale.domain.FulfillmentType;
import kg.chairx.sale.domain.Sale;
import kg.chairx.sale.domain.SaleItem;
import kg.chairx.sale.domain.SaleStatus;
import kg.chairx.inventory.cost.InventoryCostPostingService;
import kg.chairx.sale.application.SaleNotFoundException;
import kg.chairx.sale.application.SaleRuleViolationException;
import kg.chairx.sale.persistence.SaleRepository;
import kg.chairx.payment.persistence.PaymentRepository;
import kg.chairx.warehouse.application.WarehouseService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@Validated
@Transactional(readOnly = true)
public class SaleService implements kg.chairx.sale.api.DeliverySaleOperations {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Bishkek");

    private static final Comparator<SaleItem> INVENTORY_ORDER =
            Comparator.comparing(
                            (SaleItem item) ->
                                    item.warehouseId().toString()
                    )
                    .thenComparing(
                            item ->
                                    item.productVariantId().toString()
                    );

    private final SaleRepository repository;
    private final PaymentRepository payments;
    private final CustomerService customers;
    private final ProductVariantService variants;
    private final WarehouseService warehouses;
    private final InventoryService inventory;
    private final AuditService audit;
    private final SaleRequestFingerprint fingerprint;
    private final ExchangeSaleGuard exchangeSaleGuard;
    private final InventoryCostPostingService costPosting;

    public SaleService(
            SaleRepository repository,
            PaymentRepository payments,
            CustomerService customers,
            ProductVariantService variants,
            WarehouseService warehouses,
            InventoryService inventory,
            AuditService audit,
            SaleRequestFingerprint fingerprint,
            ExchangeSaleGuard exchangeSaleGuard,
            InventoryCostPostingService costPosting
    ) {
        this.repository = repository;
        this.payments = payments;
        this.customers = customers;
        this.variants = variants;
        this.warehouses = warehouses;
        this.inventory = inventory;
        this.audit = audit;
        this.fingerprint = fingerprint;
        this.exchangeSaleGuard = exchangeSaleGuard;
        this.costPosting = costPosting;
    }

    @Transactional
    public SaleResponse create(
            @Valid CreateSaleRequest request
    ) {
        String fingerprint = this.fingerprint.fingerprint(request);

        var existing = repository.findByIdempotencyKey(
                request.idempotencyKey()
        );

        if (existing.isPresent()) {
            return existingSale(
                    existing.get(),
                    fingerprint
            );
        }

        validateReferences(request);

        UUID saleId = UUID.randomUUID();

        String saleNumber = saleNumber(
                repository.nextSaleNumber()
        );

        String actor = actor();

        boolean inserted = repository.tryInsert(
                saleId,
                saleNumber,
                request.customerId(),
                request.fulfillmentType(),
                request.idempotencyKey(),
                fingerprint,
                actor
        );

        if (!inserted) {
            Sale concurrentSale =
                    repository.findByIdempotencyKey(
                                    request.idempotencyKey()
                            )
                            .orElseThrow(
                                    () -> new IllegalStateException(
                                            "Продажа с ключом идемпотентности не найдена после конфликта"
                                    )
                            );

            return existingSale(
                    concurrentSale,
                    fingerprint
            );
        }

        List<SaleItem> items = request.items()
                .stream()
                .map(item -> new SaleItem(
                        UUID.randomUUID(),
                        saleId,
                        item.productVariantId(),
                        item.warehouseId(),
                        item.quantity(),
                        item.unitSalePrice()
                ))
                .toList();

        for (SaleItem item : items) {
            repository.insertItem(item);
        }

        for (SaleItem item : items.stream()
                .sorted(INVENTORY_ORDER)
                .toList()) {

            inventory.reserve(
                    new ChangeReservedStock(
                            item.warehouseId(),
                            item.productVariantId(),
                            item.quantity()
                    )
            );
        }

        SaleResponse result = get(saleId);

        audit.record(
                "SALE",
                saleId,
                "CONFIRMED",
                null,
                result
        );

        return result;
    }

    public SaleResponse get(UUID saleId) {
        return SaleMapper.toResponse(
                repository.find(saleId)
                        .orElseThrow(
                                SaleNotFoundException::new
                        )
        );
    }

    @Transactional
    public SaleResponse fulfill(UUID saleId) {
        Sale sale = lock(saleId);

        if (sale.fulfillmentType()
                != FulfillmentType.SELF_PICKUP) {
            throw rule(
                    "DELIVERY_REQUIRED",
                    "Продажу с доставкой необходимо выдавать через процесс доставки"
            );
        }

        return fulfillLocked(sale);
    }

    @Transactional
    public SaleResponse fulfillForDelivery(
            UUID saleId
    ) {
        Sale sale = lock(saleId);

        if (sale.fulfillmentType()
                == FulfillmentType.SELF_PICKUP) {
            throw rule(
                    "DELIVERY_NOT_ALLOWED",
                    "Самовывоз нельзя выдавать через процесс доставки"
            );
        }

        return fulfillLocked(sale);
    }

    @Transactional
    public SaleResponse cancel(UUID saleId) {
        Sale sale = lock(saleId);

        if (sale.fulfillmentType()
                != FulfillmentType.SELF_PICKUP) {
            throw rule(
                    "DELIVERY_REQUIRED",
                    "Продажу с доставкой необходимо отменять через процесс доставки"
            );
        }

        return cancelLocked(sale);
    }

    @Transactional
    public SaleResponse cancelForDelivery(
            UUID saleId
    ) {
        Sale sale = lock(saleId);

        if (sale.fulfillmentType()
                == FulfillmentType.SELF_PICKUP) {
            throw rule(
                    "DELIVERY_NOT_ALLOWED",
                    "Самовывоз нельзя отменять через процесс доставки"
            );
        }

        return cancelLocked(sale);
    }

    private SaleResponse cancelLocked(
            Sale sale
    ) {
        if (sale.status() == SaleStatus.CANCELLED) {
            return SaleMapper.toResponse(sale);
        }

        if (sale.status() == SaleStatus.FULFILLED) {
            throw rule(
                    "SALE_ALREADY_FULFILLED",
                    "Выданную продажу нельзя отменить. Используйте возврат"
            );
        }

        if (sale.status() != SaleStatus.CONFIRMED) {
            throw rule(
                    "INVALID_SALE_STATUS",
                    "Продажу нельзя отменить в текущем состоянии"
            );
        }
        // PaymentService.create() takes the same Sale row lock before inserting
        // a PAID payment, serializing payment creation and sale cancellation.
        if (payments.findActiveBySale(sale.id()).isPresent()) {
            throw rule(
                    "SALE_HAS_ACTIVE_PAYMENT",
                    "Перед отменой продажи необходимо урегулировать активную оплату"
            );
        }
        exchangeSaleGuard.requireCancellationAllowed(sale.id());

        String actor = actor();

        SaleResponse before =
                SaleMapper.toResponse(sale);

        for (SaleItem item : sale.items()
                .stream()
                .sorted(INVENTORY_ORDER)
                .toList()) {

            inventory.releaseReservation(
                    new ChangeReservedStock(
                            item.warehouseId(),
                            item.productVariantId(),
                            item.quantity()
                    )
            );
        }

        repository.cancel(
                sale.id(),
                actor
        );

        SaleResponse after = get(sale.id());

        audit.record(
                "SALE",
                sale.id(),
                "CANCELLED",
                before,
                after
        );

        return after;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public SaleResponse lockForInventoryReturn(UUID saleId) {
        return SaleMapper.toResponse(lock(saleId));
    }

    private SaleResponse fulfillLocked(
            Sale sale
    ) {
        if (sale.status() == SaleStatus.FULFILLED) {
            return SaleMapper.toResponse(sale);
        }

        if (sale.status() == SaleStatus.CANCELLED) {
            throw rule(
                    "SALE_ALREADY_CANCELLED",
                    "Отменённую продажу нельзя выдать"
            );
        }

        if (sale.status() != SaleStatus.CONFIRMED) {
            throw rule(
                    "INVALID_SALE_STATUS",
                    "Продажу нельзя выдать в текущем состоянии"
            );
        }
        exchangeSaleGuard.requireFulfillmentAllowed(sale.id());

        String actor = actor();

        SaleResponse before =
                SaleMapper.toResponse(sale);

        for (SaleItem item : sale.items()
                .stream()
                .sorted(INVENTORY_ORDER)
                .toList()) {

            inventory.releaseReservation(
                    new ChangeReservedStock(
                            item.warehouseId(),
                            item.productVariantId(),
                            item.quantity()
                    )
            );

            costPosting.postSaleOut(
                    new RecordStockMovement(
                            saleOutOperationId(item.id()),
                            item.warehouseId(),
                            item.productVariantId(),
                            StockMovementType.SALE_OUT,
                            item.quantity(),
                            "SALE_ITEM",
                            item.id(),
                            actor
                    )
            );
        }

        repository.fulfill(
                sale.id(),
                actor
        );

        SaleResponse after = get(sale.id());

        audit.record(
                "SALE",
                sale.id(),
                "FULFILLED",
                before,
                after
        );

        return after;
    }

    private SaleResponse existingSale(
            Sale existing,
            String fingerprint
    ) {
        String storedFingerprint =
                repository.requestFingerprint(
                        existing.id()
                );

        if (!storedFingerprint.equals(fingerprint)) {
            throw rule(
                    "SALE_IDEMPOTENCY_CONFLICT",
                    "Ключ продажи уже использован с другими данными"
            );
        }

        return SaleMapper.toResponse(existing);
    }

    private void validateReferences(
            CreateSaleRequest request
    ) {
        if (request.customerId() != null) {
            var customer = customers.get(
                    request.customerId()
            );

            if (!customer.active()) {
                throw rule(
                        "CUSTOMER_INACTIVE",
                        "Нельзя оформить продажу на неактивного клиента"
                );
            }
        }

        var seen = new HashSet<ItemKey>();

        for (CreateSaleItemRequest item : request.items()) {
            var variant = variants.get(
                    item.productVariantId()
            );

            if (!variant.active()) {
                throw rule(
                        "PRODUCT_VARIANT_INACTIVE",
                        "Нельзя продать неактивный вариант товара"
                );
            }

            var warehouse = warehouses.get(
                    item.warehouseId()
            );

            if (!warehouse.active()) {
                throw rule(
                        "WAREHOUSE_INACTIVE",
                        "Нельзя продать товар с неактивного склада"
                );
            }

            ItemKey key = new ItemKey(
                    item.productVariantId(),
                    item.warehouseId()
            );

            if (!seen.add(key)) {
                throw rule(
                        "DUPLICATE_SALE_ITEM",
                        "Один вариант товара с одного склада нельзя указывать в продаже несколько раз"
                );
            }
        }
    }

    private Sale lock(UUID saleId) {
        return repository.lock(saleId)
                .orElseThrow(
                        SaleNotFoundException::new
                );
    }

    private String saleNumber(long sequence) {
        int year = LocalDate.now(BUSINESS_ZONE)
                .getYear();

        return "SALE-%d-%06d".formatted(
                year,
                sequence
        );
    }

    private UUID saleOutOperationId(
            UUID saleItemId
    ) {
        return UUID.nameUUIDFromBytes(
                ("SALE_OUT:" + saleItemId)
                        .getBytes(
                                StandardCharsets.UTF_8
                        )
        );
    }

    private String actor() {
        return SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();
    }

    private SaleRuleViolationException rule(
            String code,
            String message
    ) {
        return new SaleRuleViolationException(
                code,
                message
        );
    }

    private record ItemKey(
            UUID productVariantId,
            UUID warehouseId
    ) {
    }


}