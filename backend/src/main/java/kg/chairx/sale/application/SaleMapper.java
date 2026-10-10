package kg.chairx.sale.application;

import kg.chairx.sale.api.SaleItemResponse;
import kg.chairx.sale.api.SaleResponse;
import kg.chairx.sale.domain.Sale;

final class SaleMapper {

    private SaleMapper() {
    }

    static SaleResponse toResponse(Sale sale) {
        var items = sale.items()
                .stream()
                .map(item -> new SaleItemResponse(
                        item.id(),
                        item.productVariantId(),
                        item.warehouseId(),
                        item.quantity(),
                        item.unitSalePrice(),
                        item.total()
                ))
                .toList();

        return new SaleResponse(
                sale.id(),
                sale.saleNumber(),
                sale.customerId(),
                sale.fulfillmentType(),
                sale.status(),
                items,
                sale.total(),
                sale.createdBy(),
                sale.createdAt(),
                sale.fulfilledBy(),
                sale.fulfilledAt(),
                sale.cancelledBy(),
                sale.cancelledAt(),
                sale.comment()
        );
    }
}