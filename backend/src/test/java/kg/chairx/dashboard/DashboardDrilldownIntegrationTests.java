package kg.chairx.dashboard;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"CHAIRX_CATALOG_PASSWORD=integration-test-password","spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class DashboardDrilldownIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    static final ZoneId ZONE=ZoneId.of("Asia/Bishkek");
    static final SimpleGrantedAuthority[] RIGHTS={new SimpleGrantedAuthority("SALES_READ"),new SimpleGrantedAuthority("DELIVERIES_READ"),new SimpleGrantedAuthority("INVENTORY_READ")};
    JsonNode read(String path) throws Exception {
        return new JsonMapper().readTree(mvc.perform(get(path).with(user("reader").authorities(RIGHTS)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    UUID sale(String state,OffsetDateTime created) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO sales(id,sale_number,status,fulfillment_type,idempotency_key,request_fingerprint,
                created_by,created_at,fulfilled_by,fulfilled_at,cancelled_by,cancelled_at)
            VALUES (?,?,?,'CITY_DELIVERY',?,?,'drilldown-test',?,?,?,?,?)
            """,id,"DD-"+id.toString().substring(0,20),state,UUID.randomUUID(),"drilldown",created,
            state.equals("FULFILLED")?"test":null,state.equals("FULFILLED")?created:null,
            state.equals("CANCELLED")?"test":null,state.equals("CANCELLED")?created:null);
        return id;
    }
    void delivery(String state,LocalDate planned,OffsetDateTime delivered) {
        var created=LocalDate.now(ZONE).minusDays(10).atStartOfDay(ZONE).toOffsetDateTime();
        jdbc.update("""
            INSERT INTO deliveries(id,sale_id,status,recipient_name,recipient_phone,address,created_by,
                created_at,planned_delivery_date,delivered_at)
            VALUES (?,?,?,'Test','123','Test','drilldown-test',?,?,?)
            """,UUID.randomUUID(),sale("FULFILLED",created),state,created,planned,delivered);
    }
    @Test void salesMatchMetricsAndBusinessDayBoundariesBeforePagination() throws Exception {
        LocalDate today=LocalDate.now(ZONE);var start=today.atStartOfDay(ZONE).toOffsetDateTime();
        for(int i=0;i<23;i++)sale(i%2==0?"DRAFT":"CONFIRMED",start.minusDays(5));
        sale("DRAFT",start);sale("CANCELLED",start.plusHours(23));
        sale("FULFILLED",start.minusNanos(1000));sale("CONFIRMED",start.plusDays(1));
        var dashboard=read("/api/dashboard/overview");var active=read("/api/sales?activeOnly=true");
        assertThat(active.get("total").asLong()).isEqualTo(dashboard.get("activeOrders").asLong());
        assertThat(active.get("items").size()).isEqualTo(20);
        assertThat(read("/api/sales?activeOnly=true&page=1").get("items").size()).isEqualTo(5);
        for(var row:active.get("items"))assertThat(row.get("status").asText()).isIn("DRAFT","CONFIRMED");
        var daily=read("/api/sales?from="+today+"&to="+today);var series=dashboard.get("ordersByDay");
        assertThat(daily.get("total").asLong()).isEqualTo(2).isEqualTo(series.get(series.size()-1).get("orders").asLong());
        assertThat(read("/api/sales?activeOnly=true&status=FULFILLED").get("total").asLong()).isZero();
    }
    @Test void deliveriesUseCompletionDateAndExactlyPendingStatuses() throws Exception {
        LocalDate today=LocalDate.now(ZONE);var start=today.atStartOfDay(ZONE).toOffsetDateTime();
        for(int i=0;i<23;i++)delivery(i%2==0?"READY":"IN_TRANSIT",today.minusDays(1),null);
        delivery("READY",today,null);delivery("READY",null,null);
        delivery("FAILED",today.minusDays(1),null);delivery("CANCELLED",today.minusDays(1),null);
        delivery("DELIVERED",today.minusDays(1),start);delivery("DELIVERED",today.minusDays(1),start.plusDays(1).minusNanos(1000));
        delivery("DELIVERED",today.minusDays(1),start.minusNanos(1000));delivery("DELIVERED",today.minusDays(1),start.plusDays(1));
        var dashboard=read("/api/dashboard/overview");var pending=read("/api/deliveries?pendingOnly=true");
        assertThat(pending.get("total").asLong()).isEqualTo(dashboard.get("pendingDeliveries").asLong()).isEqualTo(25);
        assertThat(pending.get("items").size()).isEqualTo(20);
        assertThat(read("/api/deliveries?pendingOnly=true&page=1").get("items").size()).isEqualTo(5);
        assertThat(read("/api/deliveries?pendingOnly=true&plannedTo="+today.minusDays(1)).get("total").asLong()).isEqualTo(dashboard.get("delayedDeliveries").asLong()).isEqualTo(23);
        assertThat(read("/api/deliveries?deliveredOn="+today).get("total").asLong()).isEqualTo(dashboard.get("deliveredToday").asLong()).isEqualTo(2);
        assertThat(read("/api/deliveries?deliveredOn="+today+"&from="+today).get("total").asLong()).isZero();
        assertThat(read("/api/deliveries?pendingOnly=true&status=FAILED").get("total").asLong()).isZero();
    }
    @Test void lowStockAggregatesAcrossWarehousesBeforePaginationAndHidesCosts() throws Exception {
        UUID product=UUID.randomUUID(),first=UUID.randomUUID(),second=UUID.randomUUID();
        for(UUID id:new UUID[]{first,second})jdbc.update("INSERT INTO warehouses(id,name,code,active,created_at,updated_at) VALUES (?,?,?,true,now(),now())",id,"DD warehouse",("DD_"+id.toString().substring(0,8)).toUpperCase(java.util.Locale.ROOT));
        jdbc.update("INSERT INTO products(id,name,active,created_at,updated_at) VALUES (?, ?, true,now(),now())",product,"DD product");
        for(int i=0;i<28;i++) {
            UUID variant=UUID.randomUUID();
            jdbc.update("INSERT INTO product_variants(id,product_id,name,recommended_sale_price,active,created_at,updated_at) VALUES (?,?,?,8500,true,now(),now())",variant,product,"Variant "+i);
            int available=i<24?1:i==24?5:i==25?0:i==26?6:3;
            jdbc.update("INSERT INTO inventory_balances(warehouse_id,product_variant_id,on_hand,reserved,blocked) VALUES (?,?,?,?,?)",first,variant,available+3,2,1);
            if(i==27)jdbc.update("INSERT INTO inventory_balances(warehouse_id,product_variant_id,on_hand) VALUES (?,?,3)",second,variant);
        }
        var dashboard=read("/api/dashboard/overview");var low=read("/api/inventory/overview?lowStock=true&includeZero=true");
        assertThat(low.get("total").asLong()).isEqualTo(dashboard.get("lowStockVariants").asLong()).isEqualTo(25);
        assertThat(low.get("items").size()).isEqualTo(20);
        assertThat(read("/api/inventory/overview?lowStock=true&page=1").get("items").size()).isEqualTo(5);
        assertThat(read("/api/inventory/overview?lowStock=true&warehouseId="+first).get("total").asLong()).isEqualTo(26);
        long available=0;
        for(int page=0;;page++) {
            var rows=read("/api/inventory/overview?includeZero=false&page="+page).get("items");if(rows.isEmpty())break;
            for(var row:rows)available+=row.get("available").asLong();
        }
        assertThat(available).isEqualTo(dashboard.get("availableUnits").asLong());
        assertThat(low.toString()).doesNotContain("unitCost","profitMargin","purchaseUnitCost");
    }
    @Test void filtersRetainPermissionsAndRejectMalformedQueries() throws Exception {
        for(String path:new String[]{"/api/sales?activeOnly=true","/api/deliveries?pendingOnly=true","/api/deliveries?deliveredOn=2026-10-11","/api/inventory/overview?lowStock=true"}) {
            mvc.perform(get(path).with(anonymous())).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(user("no-rights").authorities(new SimpleGrantedAuthority("CATALOG_READ")))).andExpect(status().isForbidden());
        }
        for(String path:new String[]{"/api/deliveries?deliveredOn=2026-02-30","/api/sales?activeOnly=wat","/api/deliveries?pendingOnly=wat","/api/inventory/overview?lowStock=wat"})
            mvc.perform(get(path).with(user("reader").authorities(RIGHTS))).andExpect(status().isBadRequest());
    }
}
