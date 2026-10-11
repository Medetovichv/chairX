package kg.chairx.demo;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class DemoCatalogIsolationTest {
    @Autowired DemoCatalogSeedService seeds;
    @Autowired JdbcTemplate jdbc;

    @Test void refusesNormalTestDatabaseBeforeAnyFixtureWrite() {
        assertThat(jdbc.queryForObject("select current_database()",String.class))
                .isEqualTo("chairx_test");
        long before=jdbc.queryForObject("select count(*) from products",Long.class);
        assertThatThrownBy(seeds::seed)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside chairx_demo");
        assertThat(jdbc.queryForObject("select count(*) from products",Long.class))
                .isEqualTo(before);
    }
}
