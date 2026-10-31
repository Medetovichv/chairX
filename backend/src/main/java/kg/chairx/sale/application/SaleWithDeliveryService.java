package kg.chairx.sale.application;

import jakarta.validation.Valid;
import kg.chairx.delivery.api.CreateDeliveryRequest;
import kg.chairx.delivery.application.DeliveryService;
import kg.chairx.sale.api.CreateSaleWithDeliveryRequest;
import kg.chairx.sale.api.CreateDraftSaleRequest;
import kg.chairx.customer.application.CustomerService;
import kg.chairx.customer.api.CreateCustomerRequest;
import java.util.UUID;
import kg.chairx.sale.api.SaleResponse;
import kg.chairx.sale.domain.FulfillmentType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Single transaction: create draft, validate/reserve stock, confirm sale,
 * create delivery. If any business step fails, no sale or delivery is kept.
 */
@Service
public class SaleWithDeliveryService {
    private final SaleService sales;
    private final CustomerService customers;
    private final DeliveryService deliveries;
    private final kg.chairx.customer.application.CustomerAddressService addresses;
    private final JdbcClient jdbc;
    private final JsonMapper mapper;

    public SaleWithDeliveryService(SaleService sales,DeliveryService deliveries,
            JdbcClient jdbc,JsonMapper mapper,
            kg.chairx.customer.application.CustomerAddressService addresses,
            CustomerService customers) {
        this.sales=sales;
        this.customers=customers;
        this.deliveries=deliveries;
        this.jdbc=jdbc;
        this.mapper=mapper;
        this.addresses=addresses;
    }

    @Transactional
    public SaleResponse create(@Valid CreateSaleWithDeliveryRequest request) {
        if (request == null || request.sale() == null || request.delivery() == null
                || request.sale().idempotencyKey() == null
                || request.sale().fulfillmentType() == null
                || request.sale().fulfillmentType() == FulfillmentType.SELF_PICKUP) {
            throw new IllegalArgumentException("Для совместного оформления требуется продажа с доставкой");
        }
        String hash=hash(request);
        var key=request.sale().idempotencyKey();
        jdbc.sql("""
                INSERT INTO sale_delivery_requests(idempotency_key,request_hash)
                VALUES (:key,:hash) ON CONFLICT DO NOTHING
                """).param("key",key).param("hash",hash).update();
        String stored=jdbc.sql("""
                SELECT request_hash FROM sale_delivery_requests WHERE idempotency_key=:key
                """).param("key",key).query(String.class).single();
        if(!stored.equals(hash)) {
            throw new IllegalStateException("Ключ создания заказа уже использован с другими данными");
        }

        // A delivery with contact details must also be linked to a customer.
        // Keep the generated customer ID under the request idempotency key so
        // replaying the same request cannot create a second customer.
        CreateDraftSaleRequest saleRequest=request.sale();
        if(saleRequest.customerId()==null) {
            UUID generated=jdbc.sql("""
                    SELECT generated_customer_id FROM sale_delivery_requests
                    WHERE idempotency_key=:key FOR UPDATE
                    """).param("key",key).query(UUID.class).optional().orElse(null);
            if(generated==null) {
                var delivery=request.delivery();
                generated=customers.create(new CreateCustomerRequest(
                        delivery.recipientName(),delivery.recipientPhone(),null,null,null,
                        delivery.address(),delivery.cityRegion(),null)).id();
                jdbc.sql("""
                        UPDATE sale_delivery_requests SET generated_customer_id=:customer
                        WHERE idempotency_key=:key
                        """).param("customer",generated).param("key",key).update();
            }
            saleRequest=new CreateDraftSaleRequest(saleRequest.idempotencyKey(),
                    generated,saleRequest.fulfillmentType(),saleRequest.items(),saleRequest.comment());
        }

        // All calls participate in one transaction, including customer creation.
        var draft=sales.createDraft(saleRequest);
        var confirmed=sales.confirmDraft(draft.id());
        Boolean exists=jdbc.sql("SELECT EXISTS(SELECT 1 FROM deliveries WHERE sale_id=:id)")
                .param("id",confirmed.id()).query(Boolean.class).single();
        if (!Boolean.TRUE.equals(exists)) {
            var d=request.delivery();
            deliveries.create(new CreateDeliveryRequest(confirmed.id(),
                    d.recipientName(),d.recipientPhone(),d.address(),d.cityRegion(),
                    d.deliveryCost(),d.carrierName(),d.trackingNumber(),d.comment(),
                    d.plannedDeliveryDate()));
        }
        // Persist the destination only for a successfully created delivery.
        // Replays and repeats are safe: CustomerAddressService deduplicates.
        if (confirmed.customerId()!=null) {
            var d=request.delivery();
            addresses.add(confirmed.customerId(),new kg.chairx.customer.application.CustomerAddressService.AddressInput(d.address(),d.cityRegion()));
        }
        return confirmed;
    }

    private String hash(CreateSaleWithDeliveryRequest request) {
        var payload=mapper.writeValueAsString(request);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable",ex);
        }
    }
}
