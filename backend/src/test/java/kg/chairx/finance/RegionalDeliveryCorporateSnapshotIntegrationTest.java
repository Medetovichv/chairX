package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.finance.application.DailyClosingSalesSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #109: corporate snapshot must use the same paid regional-delivery
 * business date as employee operational reports, without duplicating revenue.
 * Runs against disposable Testcontainers PostgreSQL only.
 */
@SpringBootTest(properties = {
    "CHAIRX_CATALOG_PASSWORD=integration-test-password",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class RegionalDeliveryCorporateSnapshotIntegrationTest {
    private static final LocalDate FIRST = LocalDate.of(2020, 10, 10);
    private static final LocalDate NEXT = FIRST.plusDays(1);
    private static final Instant PAID_FIRST = Instant.parse("2020-10-10T08:00:00Z");
    private static final Instant PAID_NEXT = Instant.parse("2020-10-11T08:00:00Z");
    private static final Instant DELIVERED_LATER = Instant.parse("2020-10-13T08:00:00Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired JdbcClient client;

    private final List<UUID> sales = new ArrayList<>();
    private final List<UUID> variants = new ArrayList<>();
    private final List<UUID> products = new ArrayList<>();

    private record Fixture(UUID saleId, UUID deliveryId) {}

    private void assertDisposableDb() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
            .isEqualTo("chairx_test");
    }

    private Fixture region(String status, int deliveryFee, int paymentAmount, Instant paidAt) {
        assertDisposableDb();
        UUID product = UUID.randomUUID(), variant = UUID.randomUUID();
        UUID sale = UUID.randomUUID(), delivery = UUID.randomUUID();
        products.add(product); variants.add(variant); sales.add(sale);
        UUID warehouse = jdbc.queryForObject(
            "SELECT id FROM warehouses WHERE code='HOME'", UUID.class);
        jdbc.update("""
            INSERT INTO products(id,name,active,created_at,updated_at)
            VALUES (?,'Regional closing integration chair',TRUE,now(),now())
            """, product);
        jdbc.update("""
            INSERT INTO product_variants(id,product_id,name,recommended_sale_price,active,created_at,updated_at)
            VALUES (?,?,'Black',8500,TRUE,now(),now())
            """, variant, product);
        jdbc.update("""
            INSERT INTO sales(id,sale_number,idempotency_key,request_fingerprint,fulfillment_type,status,created_by,created_at)
            VALUES (?,?,?,?,'REGION_DELIVERY','CONFIRMED','test-operator',?)
            """, sale, "R"+sale.toString().replace("-","").substring(0,20),
            UUID.randomUUID(), "issue109", PAID_FIRST.minusSeconds(3600).atOffset(java.time.ZoneOffset.UTC));
        jdbc.update("""
            INSERT INTO sale_items(id,sale_id,product_variant_id,warehouse_id,quantity,unit_sale_price)
            VALUES (?,?,?,?,1,8500)
            """, UUID.randomUUID(), sale, variant, warehouse);
        jdbc.update("""
            INSERT INTO deliveries(id,sale_id,status,recipient_name,recipient_phone,address,
                                   delivery_cost,created_by,created_at)
            VALUES (?,?,?,'Recipient','+996700000001','Bishkek street',?,'test-operator',?)
            """, delivery,sale,status,deliveryFee,PAID_FIRST.minusSeconds(1800).atOffset(java.time.ZoneOffset.UTC));
        if (paymentAmount > 0) {
            jdbc.update("""
                INSERT INTO payments(id,sale_id,amount,method,status,paid_by,paid_at)
                VALUES (?,?,?,'CASH','PAID','test-operator',?)
                """, UUID.randomUUID(), sale, paymentAmount,paidAt.atOffset(java.time.ZoneOffset.UTC));
        }
        return new Fixture(sale,delivery);
    }

    @Test
    void fullyPaidReadyAndTransitCountOnFullPaymentDayIncludingDeliveryFee() {
        var ready = region("READY",500,9000,PAID_FIRST);
        var transit = region("IN_TRANSIT",1000,9500,PAID_FIRST);
        region("READY",500,8500,PAID_FIRST); // goods paid, delivery not paid: excluded
        var paidTomorrow = region("READY",500,9000,PAID_NEXT);

        var today = DailyClosingSalesSnapshot.live(client,FIRST);
        assertThat(today).extracting(DailyClosingSalesSnapshot.CompletedSale::saleId)
            .containsExactlyInAnyOrder(ready.saleId(),transit.saleId());
        assertThat(today).extracting(DailyClosingSalesSnapshot.CompletedSale::total)
            .containsExactlyInAnyOrder(new BigDecimal("9000.00"),new BigDecimal("9500.00"));
        assertThat(today).allSatisfy(row -> {
            assertThat(row.paymentStatus()).isEqualTo("PAID");
            assertThat(row.completedAt()).isEqualTo(PAID_FIRST);
        });
        assertThat(DailyClosingSalesSnapshot.live(client,NEXT))
            .extracting(DailyClosingSalesSnapshot.CompletedSale::saleId)
            .containsExactly(paidTomorrow.saleId());

        // Delivery handover later cannot move the already paid operational sale.
        jdbc.update("""
            UPDATE deliveries SET status='DELIVERED',delivered_at=?,delivered_by='courier'
            WHERE id=?
            """, DELIVERED_LATER.atOffset(java.time.ZoneOffset.UTC),transit.deliveryId());
        jdbc.update("""
            UPDATE sales SET status='FULFILLED',fulfilled_at=?,fulfilled_by='courier'
            WHERE id=?
            """, DELIVERED_LATER.atOffset(java.time.ZoneOffset.UTC),transit.saleId());
        assertThat(DailyClosingSalesSnapshot.live(client,FIRST))
            .extracting(DailyClosingSalesSnapshot.CompletedSale::saleId)
            .contains(transit.saleId());
        assertThat(DailyClosingSalesSnapshot.live(client,DELIVERED_LATER.atZone(
            java.time.ZoneId.of("Asia/Bishkek")).toLocalDate()))
            .extracting(DailyClosingSalesSnapshot.CompletedSale::saleId)
            .doesNotContain(transit.saleId());
    }

    @AfterEach void cleanup() {
        assertDisposableDb();
        for (UUID id : sales) {
            jdbc.update("DELETE FROM payments WHERE sale_id=?",id);
            jdbc.update("DELETE FROM deliveries WHERE sale_id=?",id);
            jdbc.update("DELETE FROM sale_items WHERE sale_id=?",id);
            jdbc.update("DELETE FROM sales WHERE id=?",id);
        }
        for (UUID id : variants) jdbc.update("DELETE FROM product_variants WHERE id=?",id);
        for (UUID id : products) jdbc.update("DELETE FROM products WHERE id=?",id);
    }
}
