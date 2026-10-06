package kg.chairx.customer.application;

import kg.chairx.customer.api.CustomerResponse;
import kg.chairx.customer.domain.Customer;

public final class CustomerMapper {

    private CustomerMapper() {
    }

    public static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.id(),
                customer.fullName(),
                customer.phone(),
                customer.secondaryPhone(),
                customer.whatsappPhone(),
                customer.instagramUsername(),
                customer.address(),
                customer.cityRegion(),
                customer.comment(),
                customer.active(),
                customer.createdAt(),
                customer.updatedAt()
        );
    }
}