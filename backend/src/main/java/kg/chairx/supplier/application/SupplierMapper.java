package kg.chairx.supplier.application;

import kg.chairx.supplier.api.SupplierResponse;
import kg.chairx.supplier.domain.Supplier;

final class SupplierMapper {
    private SupplierMapper() { }
    static SupplierResponse response(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getContactInformation(),
                supplier.getComment(), supplier.isActive(), supplier.getCreatedAt(), supplier.getUpdatedAt());
    }
}
