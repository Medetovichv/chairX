package kg.chairx.delivery.application;

import jakarta.validation.Valid;
import kg.chairx.audit.AuditService;
import kg.chairx.delivery.api.CreateDeliveryRequest;
import kg.chairx.delivery.api.DeliveryResponse;
import kg.chairx.delivery.api.DeliveryPageResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import kg.chairx.delivery.api.FailDeliveryRequest;
import kg.chairx.delivery.api.ReturnDeliveryToWarehouseRequest;
import kg.chairx.delivery.domain.Delivery;
import kg.chairx.delivery.domain.DeliveryStatus;
import kg.chairx.delivery.persistence.DeliveryRepository;
import kg.chairx.inventory.api.RecordStockMovement;
import kg.chairx.inventory.cost.InventoryCostPostingService;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.inventory.domain.StockMovementType;
import kg.chairx.sale.api.DeliverySaleOperations;
import kg.chairx.sale.api.SaleResponse;
import kg.chairx.sale.domain.FulfillmentType;
import kg.chairx.sale.domain.SaleStatus;
import kg.chairx.warehouse.application.WarehouseService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

@Service
@Validated
@Transactional(readOnly = true)
public class DeliveryService {

    private final DeliveryRepository repository;
    private final DeliverySaleOperations sales;
    private final WarehouseService warehouses;
    private final InventoryService inventory;
    private final AuditService audit;
    private final InventoryCostPostingService costPosting;

    public DeliveryService(
            DeliveryRepository repository,
            DeliverySaleOperations sales,
            WarehouseService warehouses,
            InventoryService inventory,
            AuditService audit,
            InventoryCostPostingService costPosting
    ) {
        this.repository = repository;
        this.sales = sales;
        this.warehouses = warehouses;
        this.inventory = inventory;
        this.audit = audit;
        this.costPosting = costPosting;
    }

    @Transactional
    public DeliveryResponse create(
            @Valid CreateDeliveryRequest request
    ) {
        SaleResponse sale = sales.get(
                request.saleId()
        );

        if (sale.fulfillmentType()
                == FulfillmentType.SELF_PICKUP) {
            throw rule(
                    "DELIVERY_NOT_ALLOWED",
                    "Для продажи с самовывозом доставка не создаётся"
            );
        }

        if (sale.status() != SaleStatus.CONFIRMED) {
            throw rule(
                    "INVALID_SALE_STATUS",
                    "Доставку можно создать только для подтверждённой продажи"
            );
        }

        if (repository.findBySaleId(
                request.saleId()
        ).isPresent()) {
            throw rule(
                    "DELIVERY_ALREADY_EXISTS",
                    "Для этой продажи доставка уже создана"
            );
        }

        String actor = actor();

        Delivery delivery = new Delivery(
                UUID.randomUUID(),
                request.saleId(),
                DeliveryStatus.READY,
                request.recipientName(),
                request.recipientPhone(),
                request.address(),
                request.cityRegion(),
                normalizeMoney(
                        request.deliveryCost()
                ),
                request.carrierName(),
                request.trackingNumber(),
                request.comment(),
                actor,
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                request.plannedDeliveryDate()
        );

        boolean inserted =
                repository.tryInsert(delivery);

        if (!inserted) {
            throw rule(
                    "DELIVERY_ALREADY_EXISTS",
                    "Для этой продажи доставка уже создана"
            );
        }

        DeliveryResponse result =
                DeliveryMapper.toResponse(delivery);

        audit.record(
                "DELIVERY",
                delivery.id(),
                "READY",
                null,
                result
        );

        return result;
    }

    public DeliveryPageResponse list(
            DeliveryStatus status, LocalDate from, LocalDate to, int page, int size) {
        return list(status, from, to, null, null, page, size);
    }

    public DeliveryPageResponse list(
            DeliveryStatus status, LocalDate from, LocalDate to,
            LocalDate plannedFrom, LocalDate plannedTo, int page, int size) {
        if (page < 0 || size < 1 || size > 100
                || (from != null && to != null && from.isAfter(to))
                || (plannedFrom != null && plannedTo != null && plannedFrom.isAfter(plannedTo))) {
            throw new kg.chairx.common.web.InvalidQueryException("Некорректные параметры списка доставок");
        }
        var zone = ZoneId.of("Asia/Bishkek");
        var begin = from == null ? null : from.atStartOfDay(zone).toInstant();
        var end = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        return new DeliveryPageResponse(
                repository.list(status, begin, end, plannedFrom, plannedTo, page, size),
                page, size, repository.count(status, begin, end, plannedFrom, plannedTo));
    }

    public DeliveryResponse get(
            UUID deliveryId
    ) {
        return DeliveryMapper.toResponse(
                repository.find(deliveryId)
                        .orElseThrow(
                                DeliveryNotFoundException::new
                        )
        );
    }

    public DeliveryResponse getBySaleId(
            UUID saleId
    ) {
        return DeliveryMapper.toResponse(
                repository.findBySaleId(saleId)
                        .orElseThrow(
                                DeliveryNotFoundException::new
                        )
        );
    }

    @Transactional
    public DeliveryResponse changePlannedDate(UUID deliveryId, LocalDate plannedDate) {
        Delivery delivery = lock(deliveryId);
        if (!delivery.ready() && !delivery.inTransit()) {
            throw rule("INVALID_DELIVERY_STATUS",
                    "Плановую дату можно изменить только до завершения доставки");
        }
        if (java.util.Objects.equals(delivery.plannedDeliveryDate(), plannedDate)) {
            return DeliveryMapper.toResponse(delivery);
        }

        DeliveryResponse before = DeliveryMapper.toResponse(delivery);
        repository.updatePlannedDate(deliveryId, plannedDate);
        DeliveryResponse after = get(deliveryId);
        audit.record("DELIVERY", deliveryId, "PLANNED_DATE_CHANGED", before, after);
        return after;
    }

    @Transactional
    public DeliveryResponse dispatch(
            UUID deliveryId
    ) {
        Delivery delivery = lock(deliveryId);

        if (delivery.status()
                == DeliveryStatus.IN_TRANSIT) {
            return DeliveryMapper.toResponse(
                    delivery
            );
        }

        if (!delivery.ready()) {
            throw rule(
                    "INVALID_DELIVERY_STATUS",
                    "Отправить можно только готовую доставку"
            );
        }

        DeliveryResponse before =
                DeliveryMapper.toResponse(delivery);

        /*
         * Внутри той же транзакции:
         *
         * 1. снимаются резервы;
         * 2. создаются SALE_OUT;
         * 3. Sale становится FULFILLED.
         *
         * Если любой шаг упадёт, изменение Delivery
         * также откатится.
         */
        sales.fulfillForDelivery(
                delivery.saleId()
        );

        String actor = actor();
        Instant time = Instant.now();

        repository.dispatch(
                delivery.id(),
                actor,
                time
        );

        DeliveryResponse after =
                get(delivery.id());

        audit.record(
                "DELIVERY",
                delivery.id(),
                "DISPATCHED",
                before,
                after
        );

        return after;
    }

    @Transactional
    public DeliveryResponse markDelivered(
            UUID deliveryId
    ) {
        Delivery delivery = lock(deliveryId);

        if (delivery.delivered()) {
            return DeliveryMapper.toResponse(
                    delivery
            );
        }

        if (!delivery.inTransit()) {
            throw rule(
                    "INVALID_DELIVERY_STATUS",
                    "Завершить можно только отправленную доставку"
            );
        }

        DeliveryResponse before =
                DeliveryMapper.toResponse(delivery);

        repository.deliver(
                delivery.id(),
                actor(),
                Instant.now()
        );

        DeliveryResponse after =
                get(delivery.id());

        audit.record(
                "DELIVERY",
                delivery.id(),
                "DELIVERED",
                before,
                after
        );

        return after;
    }

    @Transactional
    public DeliveryResponse markFailed(
            UUID deliveryId,
            @Valid FailDeliveryRequest request
    ) {
        Delivery delivery = lock(deliveryId);

        if (delivery.failed()) {
            return DeliveryMapper.toResponse(
                    delivery
            );
        }

        if (!delivery.inTransit()) {
            throw rule(
                    "INVALID_DELIVERY_STATUS",
                    "Неуспешной можно отметить только отправленную доставку"
            );
        }

        String reason = request.reason().trim();

        DeliveryResponse before =
                DeliveryMapper.toResponse(delivery);

        repository.fail(
                delivery.id(),
                actor(),
                Instant.now(),
                reason
        );

        DeliveryResponse after =
                get(delivery.id());

        audit.record(
                "DELIVERY",
                delivery.id(),
                "FAILED",
                before,
                after
        );

        return after;
    }

    @Transactional
    public DeliveryResponse cancel(
            UUID deliveryId
    ) {
        Delivery delivery = lock(deliveryId);

        if (delivery.cancelled()) {
            return DeliveryMapper.toResponse(
                    delivery
            );
        }

        if (!delivery.ready()) {
            throw rule(
                    "INVALID_DELIVERY_STATUS",
                    "Отменить можно только ещё не отправленную доставку"
            );
        }

        DeliveryResponse before =
                DeliveryMapper.toResponse(delivery);

        /*
         * Sale всё ещё CONFIRMED.
         * Отмена Sale освобождает весь резерв.
         *
         * Для продажи с доставкой используется отдельный
         * внутренний путь отмены Sale.
         */
        sales.cancelForDelivery(
                delivery.saleId()
        );

        repository.cancel(
                delivery.id(),
                actor(),
                Instant.now()
        );

        DeliveryResponse after =
                get(delivery.id());

        audit.record(
                "DELIVERY",
                delivery.id(),
                "CANCELLED",
                before,
                after
        );

        return after;
    }

    @Transactional
    public DeliveryResponse returnToWarehouse(
            UUID deliveryId,
            @Valid ReturnDeliveryToWarehouseRequest request
    ) {
        Delivery delivery = lock(deliveryId);

        if (!delivery.failed()) {
            throw rule(
                    "INVALID_DELIVERY_STATUS",
                    "На склад можно вернуть только товар из неуспешной доставки"
            );
        }

        if (delivery.returnedToWarehouse()) {
            return DeliveryMapper.toResponse(
                    delivery
            );
        }

        var warehouse = warehouses.get(
                request.warehouseId()
        );

        if (!warehouse.active()) {
            throw rule(
                    "WAREHOUSE_INACTIVE",
                    "Нельзя вернуть товар на неактивный склад"
            );
        }

        SaleResponse sale = sales.lockForInventoryReturn(delivery.saleId());

        if (sale.status() != SaleStatus.FULFILLED) {
            throw rule(
                    "INVALID_SALE_STATUS",
                    "Товар можно вернуть после фактической выдачи со склада"
            );
        }

        String actor = actor();

        /*
         * Сортировка сохраняет стабильный порядок блокировок,
         * как и в SaleService.
         */
        for (var item : sale.items()
                .stream()
                .sorted(
                        Comparator.comparing(
                                item -> item.productVariantId().toString()
                        )
                )
                .toList()) {

            costPosting.postReturn(
                    new RecordStockMovement(
                            returnOperationId(
                                    delivery.id(),
                                    item.id()
                            ),
                            request.warehouseId(),
                            item.productVariantId(),
                            StockMovementType.RETURN_IN,
                            item.quantity(),
                            "DELIVERY_RETURN",
                            delivery.id(),
                            actor
                    ), item.id()
            );
        }

        DeliveryResponse before =
                DeliveryMapper.toResponse(delivery);

        repository.returnToWarehouse(
                delivery.id(),
                request.warehouseId(),
                actor,
                Instant.now()
        );

        DeliveryResponse after =
                get(delivery.id());

        audit.record(
                "DELIVERY",
                delivery.id(),
                "RETURNED_TO_WAREHOUSE",
                before,
                after
        );

        return after;
    }

    private Delivery lock(
            UUID deliveryId
    ) {
        return repository.lock(deliveryId)
                .orElseThrow(
                        DeliveryNotFoundException::new
                );
    }

    private UUID returnOperationId(
            UUID deliveryId,
            UUID saleItemId
    ) {
        return UUID.nameUUIDFromBytes(
                (
                        "DELIVERY_RETURN:"
                                + deliveryId
                                + ":"
                                + saleItemId
                ).getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    private BigDecimal normalizeMoney(
            BigDecimal value
    ) {
        return value.setScale(
                0,
                RoundingMode.UNNECESSARY
        );
    }

    private String actor() {
        return SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();
    }

    private DeliveryRuleViolationException rule(
            String code,
            String message
    ) {
        return new DeliveryRuleViolationException(
                code,
                message
        );
    }
}