package kg.chairx.customer;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.customer.api.CreateCustomerRequest;
import kg.chairx.customer.api.UpdateCustomerRequest;
import kg.chairx.customer.application.CustomerNotFoundException;
import kg.chairx.customer.application.CustomerRuleViolationException;
import kg.chairx.customer.application.CustomerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@Import(PostgresTestConfiguration.class)
class CustomerTests {

    @Autowired
    CustomerService customers;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void fixture() {
        assertTestDatabase();
        reset();
    }

    @AfterEach
    void cleanup() {
        reset();
    }

    @Test
    void createsCustomerWithAllContactData() {
        var created = customers.create(
                request(
                        "  Айбек Осмонов  ",
                        " 0555123456 ",
                        " 0700123456 ",
                        " 0555123456 ",
                        " chairx_aibek ",
                        " Бишкек, ул. Киевская 10 ",
                        " Бишкек ",
                        " Постоянный клиент "
                )
        );

        assertThat(created.id()).isNotNull();
        assertThat(created.fullName()).isEqualTo("Айбек Осмонов");
        assertThat(created.phone()).isEqualTo("0555123456");
        assertThat(created.secondaryPhone()).isEqualTo("0700123456");
        assertThat(created.whatsappPhone()).isEqualTo("0555123456");
        assertThat(created.instagramUsername()).isEqualTo("chairx_aibek");
        assertThat(created.address()).isEqualTo("Бишкек, ул. Киевская 10");
        assertThat(created.cityRegion()).isEqualTo("Бишкек");
        assertThat(created.comment()).isEqualTo("Постоянный клиент");
        assertThat(created.active()).isTrue();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();

        assertThat(countCustomers()).isEqualTo(1);

        var loaded = customers.get(created.id());

        assertThat(loaded).isEqualTo(created);
    }

    @Test
    void customerCanBeCreatedWithoutPhoneOrOtherOptionalContacts() {
        var created = customers.create(
                request(
                        "Клиент без телефона",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        assertThat(created.fullName())
                .isEqualTo("Клиент без телефона");

        assertThat(created.phone()).isNull();
        assertThat(created.secondaryPhone()).isNull();
        assertThat(created.whatsappPhone()).isNull();
        assertThat(created.instagramUsername()).isNull();
        assertThat(created.address()).isNull();
        assertThat(created.cityRegion()).isNull();
        assertThat(created.comment()).isNull();
        assertThat(created.active()).isTrue();

        assertThat(countCustomers()).isEqualTo(1);
    }

    @Test
    void blankOptionalValuesAreStoredAsNull() {
        var created = customers.create(
                request(
                        "Нурбек",
                        "   ",
                        "",
                        " ",
                        "   ",
                        " ",
                        "",
                        "    "
                )
        );

        assertThat(created.phone()).isNull();
        assertThat(created.secondaryPhone()).isNull();
        assertThat(created.whatsappPhone()).isNull();
        assertThat(created.instagramUsername()).isNull();
        assertThat(created.address()).isNull();
        assertThat(created.cityRegion()).isNull();
        assertThat(created.comment()).isNull();

        assertThat(countCustomers()).isEqualTo(1);
    }

    @Test
    void differentCustomersMayUseSamePhone() {
        var first = customers.create(
                request(
                        "Первый клиент",
                        "0555000000",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        var second = customers.create(
                request(
                        "Второй клиент",
                        "0555000000",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(first.phone()).isEqualTo("0555000000");
        assertThat(second.phone()).isEqualTo("0555000000");

        assertThat(countCustomers()).isEqualTo(2);
    }

    @Test
    void updatesCustomerWithoutChangingIdentityOrCreationTime() {
        var created = customers.create(
                request(
                        "Старое имя",
                        "0555111111",
                        null,
                        null,
                        null,
                        null,
                        "Бишкек",
                        "Старый комментарий"
                )
        );

        var updated = customers.update(
                created.id(),
                new UpdateCustomerRequest(
                        "  Новое имя  ",
                        " 0700222222 ",
                        " 0777333333 ",
                        " 0700222222 ",
                        " new_instagram ",
                        " Новый адрес ",
                        " Ош ",
                        " Новый комментарий "
                )
        );

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());

        assertThat(updated.fullName()).isEqualTo("Новое имя");
        assertThat(updated.phone()).isEqualTo("0700222222");
        assertThat(updated.secondaryPhone()).isEqualTo("0777333333");
        assertThat(updated.whatsappPhone()).isEqualTo("0700222222");
        assertThat(updated.instagramUsername()).isEqualTo("new_instagram");
        assertThat(updated.address()).isEqualTo("Новый адрес");
        assertThat(updated.cityRegion()).isEqualTo("Ош");
        assertThat(updated.comment()).isEqualTo("Новый комментарий");

        assertThat(updated.active()).isTrue();

        assertThat(updated.updatedAt())
                .isAfterOrEqualTo(created.updatedAt());

        var loaded = customers.get(created.id());

        assertThat(loaded.fullName()).isEqualTo("Новое имя");
        assertThat(loaded.phone()).isEqualTo("0700222222");

        assertThat(countCustomers()).isEqualTo(1);
    }

    @Test
    void customerCanBeDeactivatedAndActivatedAgain() {
        var created = customers.create(
                request(
                        "Активный клиент",
                        "0555123456",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        var deactivated = customers.deactivate(created.id());

        assertThat(deactivated.active()).isFalse();

        assertThat(
                customers.get(created.id()).active()
        ).isFalse();

        var activated = customers.activate(created.id());

        assertThat(activated.active()).isTrue();

        assertThat(
                customers.get(created.id()).active()
        ).isTrue();

        assertThat(countCustomers()).isEqualTo(1);
    }

    @Test
    void repeatedDeactivateAndActivateAreIdempotent() {
        var created = customers.create(
                request(
                        "Клиент",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        var firstDeactivate =
                customers.deactivate(created.id());

        var secondDeactivate =
                customers.deactivate(created.id());

        assertThat(firstDeactivate.active()).isFalse();
        assertThat(secondDeactivate.active()).isFalse();

        var firstActivate =
                customers.activate(created.id());

        var secondActivate =
                customers.activate(created.id());

        assertThat(firstActivate.active()).isTrue();
        assertThat(secondActivate.active()).isTrue();

        assertThat(countCustomers()).isEqualTo(1);
    }

    @Test
    void searchesCustomerByNameCaseInsensitively() {
        customers.create(
                request(
                        "Айбек Осмонов",
                        "0555111111",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        customers.create(
                request(
                        "Бакыт Токтосунов",
                        "0555222222",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        var result = customers.search("айБЕК");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().fullName())
                .isEqualTo("Айбек Осмонов");
    }

    @Test
    void searchesCustomerByPhone() {
        customers.create(
                request(
                        "Первый",
                        "0555123456",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        customers.create(
                request(
                        "Второй",
                        "0700999999",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        var result = customers.search("123456");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().fullName())
                .isEqualTo("Первый");
    }

    @Test
    void searchesCustomerBySecondaryAndWhatsappPhone() {
        customers.create(
                request(
                        "Номер два",
                        "0555000000",
                        "0777123456",
                        "0700987654",
                        null,
                        null,
                        null,
                        null
                )
        );

        var bySecondary = customers.search("123456");

        assertThat(bySecondary).hasSize(1);
        assertThat(bySecondary.getFirst().fullName())
                .isEqualTo("Номер два");

        var byWhatsapp = customers.search("987654");

        assertThat(byWhatsapp).hasSize(1);
        assertThat(byWhatsapp.getFirst().fullName())
                .isEqualTo("Номер два");
    }

    @Test
    void searchesCustomerByInstagramUsername() {
        customers.create(
                request(
                        "Instagram клиент",
                        null,
                        null,
                        null,
                        "chair_master_kg",
                        null,
                        null,
                        null
                )
        );

        var result = customers.search("MASTER");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().instagramUsername())
                .isEqualTo("chair_master_kg");
    }

    @Test
    void blankSearchReturnsEmptyList() {
        customers.create(
                request(
                        "Клиент",
                        "0555123456",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        assertThat(customers.search(null)).isEmpty();
        assertThat(customers.search("")).isEmpty();
        assertThat(customers.search("   ")).isEmpty();
    }

    @Test
    void activeCustomersComeBeforeInactiveCustomersInSearch() {
        var inactive = customers.create(
                request(
                        "Алексей неактивный",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        customers.deactivate(inactive.id());

        customers.create(
                request(
                        "Алексей активный",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        var result = customers.search("Алексей");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).active()).isTrue();
        assertThat(result.get(1).active()).isFalse();
    }

    @Test
    void listUsesPaginationAndReportsTotalElements() {
        customers.create(
                request(
                        "Алексей",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        customers.create(
                request(
                        "Бакыт",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        customers.create(
                request(
                        "Чынгыз",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        var firstPage = customers.list(0, 2);
        var secondPage = customers.list(1, 2);

        assertThat(firstPage.page()).isZero();
        assertThat(firstPage.size()).isEqualTo(2);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.items()).hasSize(2);

        assertThat(secondPage.page()).isEqualTo(1);
        assertThat(secondPage.size()).isEqualTo(2);
        assertThat(secondPage.totalElements()).isEqualTo(3);
        assertThat(secondPage.items()).hasSize(1);

        assertThat(firstPage.items().get(0).fullName())
                .isEqualTo("Алексей");

        assertThat(firstPage.items().get(1).fullName())
                .isEqualTo("Бакыт");

        assertThat(secondPage.items().getFirst().fullName())
                .isEqualTo("Чынгыз");
    }

    @Test
    void missingCustomerIsRejected() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(
                () -> customers.get(unknown)
        )
                .isInstanceOf(
                        CustomerNotFoundException.class
                );

        assertThatThrownBy(
                () -> customers.deactivate(unknown)
        )
                .isInstanceOf(
                        CustomerNotFoundException.class
                );

        assertThatThrownBy(
                () -> customers.activate(unknown)
        )
                .isInstanceOf(
                        CustomerNotFoundException.class
                );
    }

    @Test
    void blankCustomerNameIsRejectedWithoutDatabaseChange() {
        assertThatThrownBy(
                () -> customers.create(
                        request(
                                "   ",
                                "0555123456",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        )
                )
        )
                .isInstanceOf(
                        CustomerRuleViolationException.class
                );

        assertThat(countCustomers()).isZero();
    }

    @Test
    void invalidPaginationIsRejected() {
        assertThatThrownBy(
                () -> customers.list(-1, 20)
        )
                .isInstanceOf(
                        CustomerRuleViolationException.class
                );

        assertThatThrownBy(
                () -> customers.list(0, 0)
        )
                .isInstanceOf(
                        CustomerRuleViolationException.class
                );

        assertThatThrownBy(
                () -> customers.list(0, 101)
        )
                .isInstanceOf(
                        CustomerRuleViolationException.class
                );
    }

    @Test
    void databaseConstraintRejectsBlankCustomerName() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(
                () -> jdbc.update(
                        """
                        insert into customers(
                            id,
                            full_name,
                            active,
                            created_at,
                            updated_at
                        )
                        values (?, '   ', true, now(), now())
                        """,
                        id
                )
        )
                .isInstanceOf(
                        DataAccessException.class
                );

        assertThat(countCustomers()).isZero();
    }

    @Test
    void databaseFailureWhileCreatingCustomerRollsBackTransaction() {
        jdbc.execute("""
                create function test_reject_customer()
                returns trigger
                language plpgsql
                as $$
                begin
                    raise exception 'Simulated customer failure';
                end;
                $$
                """);

        jdbc.execute("""
                create trigger test_reject_customer
                before insert on customers
                for each row
                execute function test_reject_customer()
                """);

        try {
            assertThatThrownBy(
                    () -> customers.create(
                            request(
                                    "Этот INSERT должен упасть",
                                    "0555123456",
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                            )
                    )
            )
                    .isInstanceOf(
                            DataAccessException.class
                    );

            assertThat(countCustomers()).isZero();

        } finally {
            jdbc.execute("""
                    drop trigger if exists test_reject_customer
                    on customers
                    """);

            jdbc.execute("""
                    drop function if exists test_reject_customer()
                    """);
        }
    }

    private CreateCustomerRequest request(
            String fullName,
            String phone,
            String secondaryPhone,
            String whatsappPhone,
            String instagramUsername,
            String address,
            String cityRegion,
            String comment
    ) {
        return new CreateCustomerRequest(
                fullName,
                phone,
                secondaryPhone,
                whatsappPhone,
                instagramUsername,
                address,
                cityRegion,
                comment
        );
    }

    private int countCustomers() {
        return jdbc.queryForObject(
                "select count(*) from customers",
                Integer.class
        );
    }

    private void assertTestDatabase() {
        assertThat(
                jdbc.queryForObject(
                        "select current_database()",
                        String.class
                )
        ).isEqualTo("chairx_test");
    }

    private void reset() {
        assertTestDatabase();

        jdbc.execute("""
            truncate table refunds, return_items, returns, payments, deliveries, sale_items, sales, customers
            restart identity
            """);
    }
}