package kg.chairx.demo;

import kg.chairx.customer.api.CreateCustomerRequest;
import kg.chairx.customer.application.CustomerService;
import kg.chairx.product.api.CreateProductRequest;
import kg.chairx.product.api.CreateProductVariantRequest;
import kg.chairx.product.application.ProductService;
import kg.chairx.product.application.ProductVariantService;
import kg.chairx.supplier.api.CreateSupplierRequest;
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

    public DemoCatalogSeedService(JdbcTemplate jdbc, ProductService products,
            ProductVariantService variants, SupplierService suppliers, CustomerService customers) {
        this.jdbc = jdbc;
        this.products = products;
        this.variants = variants;
        this.suppliers = suppliers;
        this.customers = customers;
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
        if (count != null && count > 0) return;

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
}
