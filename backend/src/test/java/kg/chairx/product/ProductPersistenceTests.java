package kg.chairx.product;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import java.util.UUID;
import kg.chairx.PostgresTestConfiguration;
import kg.chairx.product.domain.Product;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class ProductPersistenceTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory entities;

    @BeforeEach
    void clearTestDatabase() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("chairx_test");
        jdbc.update("delete from audit_entries");
        jdbc.update("delete from product_variants");
        jdbc.update("delete from products");
    }

    @Test
    void cleanMigrationAndSchemaValidationSucceed() {
        flyway.validate();
        assertThat(flyway.info().applied()).hasSize(9);
        assertThat(jdbc.queryForList("select tablename from pg_tables where schemaname='public'", String.class))
                .containsExactlyInAnyOrder(
                "products",
                "product_variants",
                "audit_entries",
                "flyway_schema_history",
                "warehouses",
                "inventory_balances",
                "stock_movements",
                "suppliers",
                "purchases",
                "purchase_items",
                "purchase_receipts",
                "purchase_receipt_items",
                "defects",
                        "customers",
                        "sales",
                        "sale_items"
        );
        // A second migration run must leave the existing schema alone.
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NULL", "''", "'   '"})
    void productNameConstraintsRejectDirectSql(String value) {
        assertThatThrownBy(() -> jdbc.update("""
                insert into products(id,name,active,created_at,updated_at)
                values (?, %s, true, now(), now())
                """.formatted(value), UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void variantForeignKeyAndRequiredParentAreEnforced() {
        assertThatThrownBy(() -> insertVariant(UUID.randomUUID(), "10"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertVariant(null, "10"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "NULL", "100000000000000000"})
    void priceConstraintsRejectDirectSql(String price) {
        UUID parent = insertProduct();
        assertThatThrownBy(() -> insertVariant(parent, price)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void productWithVariantsCannotBeDeletedThroughSql() {
        UUID parent = insertProduct();
        insertVariant(parent, "10");
        assertThatThrownBy(() -> jdbc.update("delete from products where id = ?", parent))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentProductEditsCannotSilentlyOverwriteEachOther() {
        UUID id = insertProduct();
        try (var first = entities.createEntityManager(); var second = entities.createEntityManager()) {
            first.getTransaction().begin();
            second.getTransaction().begin();
            var firstCopy = first.find(Product.class, id);
            var secondCopy = second.find(Product.class, id);
            firstCopy.update("Первое изменение", null, null);
            first.getTransaction().commit();
            secondCopy.update("Устаревшее изменение", null, null);
            try {
                assertThatThrownBy(second::flush).isInstanceOf(OptimisticLockException.class);
            } finally {
                second.getTransaction().rollback();
            }
        }
        assertThat(jdbc.queryForObject("select name from products where id = ?", String.class, id))
                .isEqualTo("Первое изменение");
    }

    private UUID insertProduct() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into products(id,name,active,created_at,updated_at) values (?, 'X5', true, now(), now())", id);
        return id;
    }

    private void insertVariant(UUID parent, String price) {
        jdbc.update("""
                insert into product_variants(id,product_id,name,recommended_sale_price,active,created_at,updated_at)
                values (?, ?, 'Black', %s, true, now(), now())
                """.formatted(price), UUID.randomUUID(), parent);
    }
}
