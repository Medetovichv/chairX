package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import kg.chairx.finance.application.FinanceProfitabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real PostgreSQL rows: sale in one month, return/refund/exchange in another. */
@SpringBootTest(properties={
        "CHAIRX_CATALOG_PASSWORD=integration-test-password",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"})
@Import(PostgresTestConfiguration.class)
@Transactional
class FinanceReturnReconciliationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired FinanceProfitabilityService profit;

    private record SaleFixture(UUID id,UUID item,UUID variant,UUID warehouse,UUID layer,UUID allocation) {}

    private SaleFixture sold(String day,int price,int cost){
        UUID product=UUID.randomUUID(),variant=UUID.randomUUID(),sale=UUID.randomUUID(),
             item=UUID.randomUUID(),stockIn=UUID.randomUUID(),stockOut=UUID.randomUUID(),
             layer=UUID.randomUUID(),allocation=UUID.randomUUID();
        UUID home=jdbc.queryForObject("SELECT id FROM warehouses WHERE code='HOME'",UUID.class);
        jdbc.update("INSERT INTO products(id,name,active,created_at,updated_at) VALUES (?,'Report fixture',TRUE,now(),now())",product);
        jdbc.update("INSERT INTO product_variants(id,product_id,name,recommended_sale_price,active,created_at,updated_at) VALUES (?,?,'Default',?,TRUE,now(),now())",variant,product,price);
        jdbc.update("""
            INSERT INTO sales(id,sale_number,idempotency_key,request_fingerprint,fulfillment_type,
              status,created_by,created_at,fulfilled_by,fulfilled_at)
            VALUES (?,?,?,?,'SELF_PICKUP','FULFILLED','accountant',?, 'accountant',?)
            """,sale,"MARGIN-"+sale.toString().substring(0,13),UUID.randomUUID(),"fixture",
                Timestamp.valueOf(day+" 09:00:00"),Timestamp.valueOf(day+" 10:00:00"));
        jdbc.update("""
            INSERT INTO sale_items(id,sale_id,product_variant_id,warehouse_id,quantity,unit_sale_price)
            VALUES (?,?,?,?,1,?)
            """,item,sale,variant,home,price);
        jdbc.update("""
            INSERT INTO stock_movements(id,operation_id,warehouse_id,product_variant_id,movement_type,
              quantity,source_type,source_id,actor,occurred_at)
            VALUES (?,?,?,?,1::text::text::text,1,'PURCHASE_RECEIPT',?,'accountant',?)
            """.replace("1::text::text::text","'PURCHASE_IN'"),
            stockIn,UUID.randomUUID(),home,variant,UUID.randomUUID(),Timestamp.valueOf(day+" 08:00:00"));
        jdbc.update("""
            INSERT INTO inventory_cost_layers(id,warehouse_id,product_variant_id,source_movement_id,
                quantity_received,quantity_remaining,total_cost,remaining_cost,received_at)
            VALUES (?,?,?,?,1,0,?,0,?)
            """,layer,home,variant,stockIn,cost,Timestamp.valueOf(day+" 08:00:00"));
        jdbc.update("""
            INSERT INTO stock_movements(id,operation_id,warehouse_id,product_variant_id,movement_type,
              quantity,source_type,source_id,actor,occurred_at)
            VALUES (?,?,? ,?,'SALE_OUT',1,'SALE_ITEM',?,'accountant',?)
            """,stockOut,UUID.randomUUID(),home,variant,item,Timestamp.valueOf(day+" 10:00:00"));
        jdbc.update("""
            INSERT INTO inventory_cost_allocations(id,stock_movement_id,cost_layer_id,quantity,allocated_cost)
            VALUES (?,?,?,1,?)
            """,allocation,stockOut,layer,cost);
        return new SaleFixture(sale,item,variant,home,layer,allocation);
    }

    private UUID returned(SaleFixture original,String day,boolean valued){
        UUID id=UUID.randomUUID(),ri=UUID.randomUUID(),movement=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO returns(id,sale_id,warehouse_id,idempotency_key,request_fingerprint,
                reason,created_by,created_at)
            VALUES (?,?,?,?,?,'Customer return','accountant',?)
            """,id,original.id(),original.warehouse(),UUID.randomUUID(),"fixture",
             Timestamp.valueOf(day+" 12:00:00"));
        jdbc.update("""
            INSERT INTO return_items(id,return_id,sale_item_id,quantity,condition)
            VALUES (?,?,?,1,'SELLABLE')
            """,ri,id,original.item());
        jdbc.update("""
            INSERT INTO stock_movements(id,operation_id,warehouse_id,product_variant_id,movement_type,
                quantity,source_type,source_id,actor,occurred_at)
            VALUES (?,?,?,?,'RETURN_IN',1,'SALE_RETURN',?,'accountant',?)
            """,movement,ri,original.warehouse(),original.variant(),id,Timestamp.valueOf(day+" 12:00:00"));
        if(valued){
            jdbc.update("""
                INSERT INTO inventory_cost_restorations(return_movement_id,original_allocation_id,quantity,amount)
                VALUES (?,?,1,600)
                """,movement,original.allocation());
        }
        return id;
    }

    @Test void returnNextMonthReversesHistoricalSaleWithoutReversingCashRefundTwice(){
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("chairx_test");
        var sale=sold("2024-01-15",1000,600);
        var january=profit.calculate(LocalDate.parse("2024-01-01"),LocalDate.parse("2024-02-01"));
        assertThat(january.grossRevenue()).isEqualByComparingTo("1000");
        assertThat(january.grossProfit()).isEqualByComparingTo("400");
        assertThat(january.netProfitAvailable()).isTrue();

        UUID returnId=returned(sale,"2024-02-05",true);
        jdbc.update("""
            INSERT INTO refunds(id,sale_id,return_id,amount,method,reason,idempotency_key,
                request_fingerprint,refunded_by,refunded_at)
            VALUES (?,?,?,1000,'CASH','Returned chair',?,?,'accountant',?)
            """,UUID.randomUUID(),sale.id(),returnId,UUID.randomUUID(),"fixture",
                Timestamp.valueOf("2024-02-06 11:00:00"));
        var february=profit.calculate(LocalDate.parse("2024-02-01"),LocalDate.parse("2024-03-01"));
        assertThat(february.grossRevenue()).isEqualByComparingTo("0");
        assertThat(february.returnedRevenue()).isEqualByComparingTo("1000");
        assertThat(february.restoredFifoCost()).isEqualByComparingTo("600");
        assertThat(february.refundOnlyDiscounts()).isEqualByComparingTo("0");
        assertThat(february.netRevenue()).isEqualByComparingTo("-1000");
        assertThat(february.grossProfit()).isEqualByComparingTo("-400");
        assertThat(february.operatingProfit()).isEqualByComparingTo("-400");
        assertThat(february.netProfitAvailable()).isTrue();
    }

    @Test void missingReturnRestorationCannotProduceVerifiedProfit(){
        var sale=sold("2024-01-15",1000,600);
        returned(sale,"2024-02-05",false);
        var report=profit.calculate(LocalDate.parse("2024-02-01"),LocalDate.parse("2024-03-01"));
        assertThat(report.netProfitAvailable()).isFalse();
        assertThat(report.operatingProfit()).isNull();
    }

    @Test void standaloneRefundIsDiscountAndDoesNotRestoreInventory(){
        var sale=sold("2024-01-15",1000,600);
        jdbc.update("""
            INSERT INTO refunds(id,sale_id,amount,method,reason,idempotency_key,
                request_fingerprint,refunded_by,refunded_at)
            VALUES (?,?,200,'BANK','Price concession',?,?,'accountant',?)
            """,UUID.randomUUID(),sale.id(),UUID.randomUUID(),"fixture",
                Timestamp.valueOf("2024-02-06 11:00:00"));
        var report=profit.calculate(LocalDate.parse("2024-02-01"),LocalDate.parse("2024-03-01"));
        assertThat(report.refundOnlyDiscounts()).isEqualByComparingTo("200");
        assertThat(report.restoredFifoCost()).isEqualByComparingTo("0");
        assertThat(report.grossProfit()).isEqualByComparingTo("-200");
        assertThat(report.netProfitAvailable()).isTrue();
    }
}
