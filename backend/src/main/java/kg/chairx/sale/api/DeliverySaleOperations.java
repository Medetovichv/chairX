package kg.chairx.sale.api;

import java.util.UUID;

/**
 * Sale operations needed by the delivery workflow.
 * Implementations retain transaction boundaries and business validations.
 */
public interface DeliverySaleOperations {
    SaleResponse get(UUID saleId);
    SaleResponse fulfillForDelivery(UUID saleId);
    SaleResponse cancelForDelivery(UUID saleId);
    SaleResponse lockForInventoryReturn(UUID saleId);
}
