package kg.chairx.demo;

import kg.chairx.customer.api.CreateCustomerRequest;
import kg.chairx.customer.application.CustomerService;
import kg.chairx.product.api.CreateProductRequest;
import kg.chairx.product.api.CreateProductVariantRequest;
import kg.chairx.product.application.ProductService;
import kg.chairx.product.application.ProductVariantService;
import kg.chairx.supplier.api.CreateSupplierRequest;
import kg.chairx.purchase.api.*;
import kg.chairx.purchase.application.PurchaseService;
import kg.chairx.operations.ReceivePurchase;
import kg.chairx.sale.api.CreateSaleRequest;
import kg.chairx.sale.api.CreateSaleItemRequest;
import kg.chairx.sale.application.SaleService;
import kg.chairx.sale.domain.FulfillmentType;
import java.util.UUID;
import java.util.List;
import kg.chairx.supplier.application.SupplierService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Phase-one catalog demo fixture. The database-name guard is checked before
 * any writes, including the marker table. All business writes and marker
 * insertion are committed atomically.
 */
@Service
public class DemoCatalogSeedService {
    private final JdbcTemplate jdbc;
    private final ProductService products;
    private final ProductVariantService variants;
    private final SupplierService suppliers;
    private final CustomerService customers;
    private final PurchaseService purchases;
    private final ReceivePurchase receiving;
    private final SaleService sales;

    public DemoCatalogSeedService(JdbcTemplate jdbc, ProductService products,
            ProductVariantService variants, SupplierService suppliers, CustomerService customers,
            PurchaseService purchases, ReceivePurchase receiving, SaleService sales) {
        this.jdbc = jdbc;
        this.products = products;
        this.variants = variants;
        this.suppliers = suppliers;
        this.customers = customers;
        this.purchases = purchases;
        this.receiving = receiving;
        this.sales = sales;
    }

    @Transactional
    public void seed() {
        String db = jdbc.queryForObject("select current_database()", String.class);
        if (!"chairx_demo".equals(db)) {
            throw new IllegalStateException("Refusing demo seeding outside chairx_demo database");
        }
        jdbc.queryForObject("select pg_advisory_xact_lock(758103, 1)", Object.class);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS chairx_demo_seed_versions (
                    version integer PRIMARY KEY,
                    installed_at timestamptz NOT NULL DEFAULT now()
                )
                """);
        Integer count = jdbc.queryForObject(
                "select count(*) from chairx_demo_seed_versions where version=1", Integer.class);
        if (count == null || count == 0) seedCatalog();
        Integer purchasesInstalled = jdbc.queryForObject(
                "select count(*) from chairx_demo_seed_versions where version=2", Integer.class);
        if (purchasesInstalled == null || purchasesInstalled == 0) seedPurchases();
        Integer salesInstalled = jdbc.queryForObject(
                "select count(*) from chairx_demo_seed_versions where version=3", Integer.class);
        if (salesInstalled == null || salesInstalled == 0) seedSales();
    }

    private void seedCatalog() {

        suppliers.create(new CreateSupplierRequest("DEMO · Фабрика кресел", "Демо-поставщик", null));
        suppliers.create(new CreateSupplierRequest("DEMO · Поставщик Б", "Демо-поставщик", null));

        customers.create(new CreateCustomerRequest("DEMO · Айбек", "+996700000001",
                null, null, null, "Бишкек", "Бишкек", "Учебный клиент"));
        customers.create(new CreateCustomerRequest("DEMO · Алина", "+996700000002",
                null, null, null, "Бишкек", "Бишкек", "Учебный клиент"));
        customers.create(new CreateCustomerRequest("DEMO · Нурбек", "+996700000003",
                null, null, null, "Ош", "Ош", "Учебный клиент"));

        var classic = products.create(new CreateProductRequest(
                "DEMO · Ergo Classic", "Учебная модель", "Офисное кресло"));
        variants.create(classic.id(), new CreateProductVariantRequest(
                "Черный", "DEMO-ERGO-BLK", "Черный", new BigDecimal("8500")));
        variants.create(classic.id(), new CreateProductVariantRequest(
                "Серый", "DEMO-ERGO-GRY", "Серый", new BigDecimal("8500")));

        var comfort = products.create(new CreateProductRequest(
                "DEMO · Comfort Plus", "Учебная модель", "Офисное кресло"));
        variants.create(comfort.id(), new CreateProductVariantRequest(
                "Бежевый", "DEMO-COMFORT-BGE", "Бежевый", new BigDecimal("10900")));

        var pro = products.create(new CreateProductRequest(
                "DEMO · Chair Pro", "Учебная модель", "Офисное кресло"));
        variants.create(pro.id(), new CreateProductVariantRequest(
                "Синий", "DEMO-PRO-BLU", "Синий", new BigDecimal("12500")));

        jdbc.update("insert into chairx_demo_seed_versions(version) values (1)");
    }

    private void seedPurchases() {
        UUID supplier = jdbc.queryForObject(
                "select id from suppliers where name='DEMO · Фабрика кресел' order by created_at limit 1",UUID.class);
        UUID black = jdbc.queryForObject(
                "select id from product_variants where sku='DEMO-ERGO-BLK' limit 1",UUID.class);
        UUID gray = jdbc.queryForObject(
                "select id from product_variants where sku='DEMO-ERGO-GRY' limit 1",UUID.class);
        UUID home = jdbc.queryForObject(
                "select id from warehouses where code='HOME' limit 1",UUID.class);
        UUID office = jdbc.queryForObject(
                "select id from warehouses where code='OFFICE' limit 1",UUID.class);

        // Distinct real business states: a draft, a partially received order,
        // and a fully received order across both warehouses.
        purchases.create(new CreatePurchaseRequest(supplier,
                List.of(new PurchaseItemRequest(gray,4,new BigDecimal("5100"))),
                BigDecimal.ZERO,"DEMO · Черновик закупки"));

        var partial=purchases.confirm(purchases.create(new CreatePurchaseRequest(supplier,
                List.of(new PurchaseItemRequest(black,10,new BigDecimal("5000"))),
                BigDecimal.ZERO,"DEMO · Частичное поступление")).id());
        receiving.receive(partial.id(),new CreatePurchaseReceiptRequest(
                UUID.randomUUID(),home,
                List.of(new ReceiptItemRequest(partial.items().getFirst().id(),5)),
                "DEMO · Первая партия"));

        var complete=purchases.confirm(purchases.create(new CreatePurchaseRequest(supplier,
                List.of(new PurchaseItemRequest(gray,8,new BigDecimal("5100"))),
                BigDecimal.ZERO,"DEMO · Закупка на два склада")).id());
        receiving.receive(complete.id(),new CreatePurchaseReceiptRequest(
                UUID.randomUUID(),home,
                List.of(new ReceiptItemRequest(complete.items().getFirst().id(),4)),
                "DEMO · Домашний склад"));
        receiving.receive(complete.id(),new CreatePurchaseReceiptRequest(
                UUID.randomUUID(),office,
                List.of(new ReceiptItemRequest(complete.items().getFirst().id(),4)),
                "DEMO · Офисный склад"));

        jdbc.update("insert into chairx_demo_seed_versions(version) values (2)");
    }
    private void seedSales() {
        UUID black = jdbc.queryForObject(
                "select id from product_variants where sku='DEMO-ERGO-BLK' limit 1",UUID.class);
        UUID gray = jdbc.queryForObject(
                "select id from product_variants where sku='DEMO-ERGO-GRY' limit 1",UUID.class);
        UUID home = jdbc.queryForObject(
                "select id from warehouses where code='HOME' limit 1",UUID.class);
        UUID office = jdbc.queryForObject(
                "select id from warehouses where code='OFFICE' limit 1",UUID.class);
        UUID aybek = jdbc.queryForObject(
                "select id from customers where phone='+996700000001' limit 1",UUID.class);
        UUID alina = jdbc.queryForObject(
                "select id from customers where phone='+996700000002' limit 1",UUID.class);

        // Each order has a genuine stock reservation; never fabricate order states.
        sales.create(new CreateSaleRequest(UUID.randomUUID(),aybek,
                FulfillmentType.SELF_PICKUP,
                List.of(new CreateSaleItemRequest(black,home,1,new BigDecimal("8500")))));
        sales.create(new CreateSaleRequest(UUID.randomUUID(),alina,
                FulfillmentType.CITY_DELIVERY,
                List.of(new CreateSaleItemRequest(gray,office,1,new BigDecimal("8500")))));

        jdbc.update("insert into chairx_demo_seed_versions(version) values (3)");
    }

}
