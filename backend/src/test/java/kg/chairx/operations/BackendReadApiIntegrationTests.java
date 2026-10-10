package kg.chairx.operations;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.inventory.cost.InventoryAdjustmentService;
import kg.chairx.inventory.application.InventoryService;
import kg.chairx.sale.api.*;
import kg.chairx.sale.application.SaleService;
import kg.chairx.sale.domain.FulfillmentType;
import kg.chairx.delivery.api.CreateDeliveryRequest;
import kg.chairx.delivery.application.DeliveryService;
import kg.chairx.returning.api.*;
import kg.chairx.returning.application.ReturnService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@WithMockUser(username="p20-api-tester")
class BackendReadApiIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired SaleService sales;
    @Autowired DeliveryService deliveries;
    @Autowired ReturnService returns;
    @Autowired InventoryService stock;
    @Autowired InventoryAdjustmentService adjustments;

    UUID product,variant,customer,warehouse;

    @BeforeEach void fixture() {
        assertThat(jdbc.queryForObject("select current_database()",String.class))
                .isEqualTo("chairx_test");
        clear();
        product=UUID.randomUUID();variant=UUID.randomUUID();customer=UUID.randomUUID();
        warehouse=jdbc.queryForObject("select id from warehouses where code='HOME'",UUID.class);
        jdbc.update("insert into products(id,name,active,created_at,updated_at) values(?,'P20 API Product',true,now(),now())",product);
        jdbc.update("""
                insert into product_variants(id,product_id,name,recommended_sale_price,active,created_at,updated_at)
                values(?,?,'Black',8500,true,now(),now())
                """,variant,product);
        jdbc.update("""
                insert into customers(id,full_name,phone,active,created_at,updated_at)
                values(?,'P20 API Customer','+996555123455',true,now(),now())
                """,customer);
        adjustments.recordValuedAdjustmentIn(UUID.randomUUID(),warehouse,variant,
                10,new BigDecimal("50000"),"p20-api-test");
    }

    @AfterEach void cleanup() {
        clear();
        jdbc.update("delete from customers where id=?",customer);
        jdbc.update("delete from product_variants where id=?",variant);
        jdbc.update("delete from products where id=?",product);
    }

    @Test void warehouseAndMovementReadsIncludeDerivedAvailableWithoutStockMutation()
            throws Exception {
        long before=jdbc.queryForObject("select count(*) from stock_movements",Long.class);
        mvc.perform(get("/api/inventory/balances")
                .param("warehouseId",warehouse.toString()).param("page","0").param("size","2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].onHand").value(10))
                .andExpect(jsonPath("$.items[0].available").value(10));
        mvc.perform(get("/api/inventory/balances/"+warehouse+"/"+variant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reserved").value(0))
                .andExpect(jsonPath("$.blocked").value(0))
                .andExpect(jsonPath("$.available").value(10));
        mvc.perform(get("/api/inventory/movements")
                .param("warehouseId",warehouse.toString()).param("variantId",variant.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        assertThat(jdbc.queryForObject("select count(*) from stock_movements",Long.class))
                .isEqualTo(before);
    }

    @Test void paginatedSalesDeliveriesAndReturnsCanBeQueried() throws Exception {
        var sale=sales.create(new CreateSaleRequest(UUID.randomUUID(),customer,
                FulfillmentType.SELF_PICKUP,
                List.of(new CreateSaleItemRequest(
                        variant,warehouse,1,new BigDecimal("8500")))));
        sales.fulfill(sale.id());
        var returned=returns.create(new CreateReturnRequest(
                sale.id(),warehouse,UUID.randomUUID(),
                List.of(new CreateReturnItemRequest(
                        sale.items().getFirst().id(),1,
                        kg.chairx.returning.domain.ReturnCondition.SELLABLE)),
                "Customer changed mind",null));
        var deliverySale=sales.create(new CreateSaleRequest(UUID.randomUUID(),customer,
                FulfillmentType.CITY_DELIVERY,
                List.of(new CreateSaleItemRequest(
                        variant,warehouse,1,new BigDecimal("8500")))));
        var delivery=deliveries.create(new CreateDeliveryRequest(
                deliverySale.id(),"Recipient","+996555333333","Bishkek",
                "Bishkek",BigDecimal.ZERO,null,null,null));

        mvc.perform(get("/api/sales").param("page","0").param("size","10")
                        .param("number",sale.saleNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(sale.id().toString()))
                .andExpect(jsonPath("$.items[0].total").value(8500));
        mvc.perform(get("/api/deliveries").param("status","READY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(delivery.id().toString()));
        mvc.perform(get("/api/returns").param("saleId",sale.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(returned.id().toString()));
    }

    @Test void defectApiWaitResolveAndWriteOffKeepCorrectStockState() throws Exception {
        String body="""
                {"warehouseId":"%s","productVariantId":"%s",
                 "quantity":2,"description":"Damaged mechanism"}
                """.formatted(warehouse,variant);
        var payload=mvc.perform(post("/api/defects").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        UUID id=UUID.fromString(new tools.jackson.databind.json.JsonMapper()
                .readTree(payload).get("id").asText());
        assertThat(stock.getBalance(warehouse,variant).blocked()).isEqualTo(2);
        mvc.perform(get("/api/defects/"+id)).andExpect(status().isOk());
        mvc.perform(get("/api/defects").param("status","OPEN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id.toString()));
        mvc.perform(post("/api/defects/"+id+"/wait-for-parts").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_PARTS"));
        mvc.perform(post("/api/defects/"+id+"/resolve").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolutionNote\":\"Fixed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        assertThat(stock.getBalance(warehouse,variant).blocked()).isZero();

        mvc.perform(post("/api/defects/"+id+"/write-off").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolutionNote\":\"Not repairable\"}"))
                .andExpect(status().isConflict());

        var writeOff=jdbc.queryForObject(
                "select count(*) from stock_movements where movement_type='WRITE_OFF'",
                Long.class);
        assertThat(writeOff).isZero();
        assertThat(stock.getBalance(warehouse,variant).onHand()).isEqualTo(10);
    }

    private void clear() {
        jdbc.execute("""
                TRUNCATE TABLE inventory_transfer_cost_origins,
                    inventory_transfers,inventory_cost_movements,
                    inventory_cost_allocations,inventory_cost_restorations,
                    inventory_cost_write_offs,inventory_cost_layers,defects,
                    exchange_settlements,exchanges,refunds,return_items,returns,
                    payments,deliveries,sale_items,sales,stock_movements,
                    inventory_balances
                RESTART IDENTITY
                """);
        jdbc.update("""
                delete from audit_entries where entity_type in (
                    'STOCK_MOVEMENT','SALE','RETURN','DELIVERY','DEFECT')
                """);
    }
}
