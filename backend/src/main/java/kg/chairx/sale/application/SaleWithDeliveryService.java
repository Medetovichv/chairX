package kg.chairx.sale.application;

import jakarta.validation.Valid;
import kg.chairx.delivery.api.CreateDeliveryRequest;
import kg.chairx.delivery.application.DeliveryService;
import kg.chairx.sale.api.CreateSaleWithDeliveryRequest;
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
    private final DeliveryService deliveries;
    private final kg.chairx.customer.application.CustomerAddressService addresses;
    private final JdbcClient jdbc;
    private final JsonMapper mapper;

    public SaleWithDeliveryService(SaleService sales,DeliveryService deliveries,
            JdbcClient jdbc,JsonMapper mapper,
            kg.chairx.customer.application.CustomerAddressService addresses) {
        this.sales=sales;
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

        // All calls participate in this transaction; failure of delivery rolls
        // back confirmation and stock reservations as well as the draft.
        var draft=sales.createDraft(request.sale());
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
