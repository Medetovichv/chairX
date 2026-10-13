package kg.chairx.finance;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PostgreSQL/Testcontainers: no global-money disclosure and no write side effects. */
@SpringBootTest(properties={
    "CHAIRX_CATALOG_PASSWORD=integration-test-password",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class EmployeeDailyClosingIsolationTest {
    private static final UUID EMPLOYEE_ROLE=UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID MANAGER_ROLE=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String PASSWORD="test-password";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    final List<UUID> users=new ArrayList<>();
    final List<UUID> sales=new ArrayList<>();
    final List<UUID> movements=new ArrayList<>();
    final List<UUID> expenses=new ArrayList<>();
    final LocalDate day=LocalDate.now(ZoneId.of("Asia/Bishkek"));

    @BeforeEach void checkTestDb(){
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("chairx_test");
    }
    @AfterEach void cleanup(){
        for(var id:users){
            jdbc.update("DELETE FROM employee_daily_closing_sales WHERE closing_id IN (SELECT id FROM employee_daily_closings WHERE employee_id=?)",id);
            jdbc.update("DELETE FROM employee_daily_closing_expenses WHERE closing_id IN (SELECT id FROM employee_daily_closings WHERE employee_id=?)",id);
            jdbc.update("DELETE FROM audit_entries WHERE entity_type='EMPLOYEE_DAILY_CLOSING' AND entity_id IN (SELECT id FROM employee_daily_closings WHERE employee_id=?)",id);
            jdbc.update("DELETE FROM employee_daily_closings WHERE employee_id=?",id);
        }
        for(var id:movements)jdbc.update("DELETE FROM finance_movements WHERE id=?",id);
        for(var id:expenses)jdbc.update("DELETE FROM expenses WHERE id=?",id);
        for(var id:sales)jdbc.update("DELETE FROM sales WHERE id=?",id);
        for(var id:users){
            jdbc.update("DELETE FROM security_user_roles WHERE user_id=?",id);
            jdbc.update("DELETE FROM app_users WHERE id=?",id);
        }
    }
    String user(UUID role){
        UUID id=UUID.randomUUID();users.add(id);
        String username="person_"+id.toString().replace("-","").substring(0,20);
        jdbc.update("""
            INSERT INTO app_users(id,username,password_hash,display_name,active)
            VALUES (?,?,?,?,TRUE)
            """,id,username,PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(PASSWORD),"Fixture");
        jdbc.update("INSERT INTO security_user_roles(user_id,role_id) VALUES (?,?)",id,role);
        return username;
    }
    void fulfilled(String actor){
        UUID id=UUID.randomUUID(); sales.add(id);
        String number="P"+id.toString().replace("-","").substring(0,16);
        jdbc.update("""
            INSERT INTO sales(id,sale_number,idempotency_key,request_fingerprint,fulfillment_type,status,created_by,created_at,fulfilled_by,fulfilled_at)
            VALUES (?,?,?,?,'SELF_PICKUP','FULFILLED',?,clock_timestamp(),?,clock_timestamp())
            """,id,number,UUID.randomUUID(),"fixture",actor,actor);
    }
    void collection(String actor,String account,int money){
        UUID id=UUID.randomUUID();movements.add(id);
        jdbc.update("""
            INSERT INTO finance_movements(id,account_code,amount,movement_type,source_type,
               source_id,created_by,business_date,business_date_source)
            VALUES (?,?,?,'SALE_PAYMENT','PAYMENT',?,?,?,'POSTING_DATE')
            """,id,account,money,UUID.randomUUID(),actor,day);
    }
    void expense(String actor,String method,int amount) {
        UUID id=UUID.randomUUID();expenses.add(id);
        jdbc.update("""
          INSERT INTO expenses(id,category,amount,payment_method,expense_date,comment,created_by,created_at)
          VALUES (?,'OTHER',?,?,?,'Personal fixture',?,clock_timestamp())
          """,id,amount,method,day,actor);
    }
    String body(String expectedCash,String expectedBank,String actualCash,String actualBank){
        return "{\"expectedCash\":\""+expectedCash+"\",\"expectedBank\":\""+expectedBank+
            "\",\"actualCash\":\""+actualCash+"\",\"actualBank\":\""+actualBank+
            "\",\"cashNote\":null,\"bankNote\":null}";
    }

    @Test void combinedPersonalClosingStoresOneActualTotalWithoutInventedAccountSplit() throws Exception {
        String employee=user(EMPLOYEE_ROLE);
        collection(employee,"CASH",600);
        collection(employee,"BANK",400);
        expense(employee,"CASH",100);
        String endpoint="/api/finance/my-closings/"+day;
        String invalid="{\"expectedCash\":\"500\",\"expectedBank\":\"400\","
            +"\"actualCash\":null,\"actualBank\":null,\"actualTotal\":\"850\","
            +"\"totalNote\":null,\"cashNote\":null,\"bankNote\":null}";
        mvc.perform(post(endpoint).with(httpBasic(employee,PASSWORD)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(invalid))
            .andExpect(status().isBadRequest());
        String valid=invalid.replace("\"totalNote\":null",
            "\"totalNote\":\"Не хватает 50 сом после сверки\"");
        mvc.perform(post(endpoint).with(httpBasic(employee,PASSWORD)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(valid))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.expectedTotal").value(900))
            .andExpect(jsonPath("$.actualTotal").value(850))
            .andExpect(jsonPath("$.expenses.length()").value(1));
        UUID employeeId=jdbc.queryForObject("SELECT id FROM app_users WHERE username=?",UUID.class,employee);
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM employee_daily_closings WHERE employee_id=?
            AND actual_cash IS NULL AND actual_bank IS NULL AND actual_total=850
            """,Integer.class,employeeId)).isEqualTo(1);
    }

    @Test void employeeSeesAllSalesButOnlyOwnCollectionsAndNoGlobalFinance() throws Exception {
        String alice=user(EMPLOYEE_ROLE),bob=user(EMPLOYEE_ROLE),manager=user(MANAGER_ROLE);
        fulfilled(alice);fulfilled(bob);
        collection(alice,"CASH",300);collection(bob,"BANK",700);
        String url="/api/finance/my-closings/"+day;
        mvc.perform(get(url).with(httpBasic(alice,PASSWORD))).andExpect(status().isOk())
            .andExpect(jsonPath("$.closed").value(false))
            .andExpect(jsonPath("$.expectedCash").value(300))
            .andExpect(jsonPath("$.expectedBank").value(0))
            .andExpect(jsonPath("$.orders").value(0))
            .andExpect(jsonPath("$.sales.length()").value(0));
        mvc.perform(get(url).with(httpBasic(bob,PASSWORD))).andExpect(status().isOk())
            .andExpect(jsonPath("$.expectedCash").value(0))
            .andExpect(jsonPath("$.expectedBank").value(700))
            .andExpect(jsonPath("$.orders").value(0));
        for(String path:List.of("/api/finance/closings","/api/finance/closings/"+day,
             "/api/finance/closings/"+day+"/preview","/api/finance/closings/"+day+"/sales",
             "/api/finance/closings/"+day+"/access")){
            mvc.perform(get(path).with(httpBasic(alice,PASSWORD))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/finance/closings/"+day).with(httpBasic(alice,PASSWORD))
             .with(csrf()).contentType(MediaType.APPLICATION_JSON)
             .content("{\"actualCash\":0,\"actualBank\":0}"))
             .andExpect(status().isForbidden());
        mvc.perform(get("/api/finance/closings").with(httpBasic(manager,PASSWORD)))
             .andExpect(status().isOk());
        mvc.perform(get(url).with(httpBasic(manager,PASSWORD)))
             .andExpect(status().isOk());
        mvc.perform(post(url).with(httpBasic(manager,PASSWORD)).with(csrf())
             .contentType(MediaType.APPLICATION_JSON)
             .content(body("0","0","0","0")))
             .andExpect(status().isForbidden());
        mvc.perform(post("/api/finance/closings/"+day).with(httpBasic(manager,PASSWORD))
             .with(csrf()).contentType(MediaType.APPLICATION_JSON)
             .content("{\"actualCash\":0,\"actualBank\":0}"))
             .andExpect(status().isForbidden());
    }

    @Test void personalCloseIsImmutableIsolatedIdempotentAndNeverPostsCompanyMoney() throws Exception {
        String a=user(EMPLOYEE_ROLE),b=user(EMPLOYEE_ROLE);
        fulfilled(a);fulfilled(b);
        collection(a,"CASH",500);collection(b,"CASH",900);
        BigDecimal beforeCash=jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='CASH'",BigDecimal.class);
        BigDecimal beforeBank=jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='BANK'",BigDecimal.class);
        int beforeJournal=jdbc.queryForObject("SELECT COUNT(*) FROM finance_movements",Integer.class);
        String url="/api/finance/my-closings/"+day;
        mvc.perform(post(url).with(httpBasic(a,PASSWORD)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("500","0","500","0")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.closed").value(true))
            .andExpect(jsonPath("$.expectedCash").value(500)).andExpect(jsonPath("$.orders").value(0));
        mvc.perform(post(url).with(httpBasic(a,PASSWORD)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("500","0","500","0")))
            .andExpect(status().isConflict());
        mvc.perform(get(url).with(httpBasic(b,PASSWORD))).andExpect(status().isOk())
            .andExpect(jsonPath("$.closed").value(false))
            .andExpect(jsonPath("$.expectedCash").value(900));
        mvc.perform(get("/api/finance/my-closings").with(httpBasic(a,PASSWORD)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value(day.toString()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee_daily_closings",Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='CASH'",BigDecimal.class))
            .isEqualByComparingTo(beforeCash);
        assertThat(jdbc.queryForObject("SELECT balance FROM finance_accounts WHERE code='BANK'",BigDecimal.class))
            .isEqualByComparingTo(beforeBank);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM finance_movements",Integer.class)).isEqualTo(beforeJournal);
        collection(a,"BANK",50);
        mvc.perform(get(url).with(httpBasic(a,PASSWORD))).andExpect(status().isOk())
            .andExpect(jsonPath("$.expectedBank").value(0))
            .andExpect(jsonPath("$.collectionsChangedAfterClose").value(true))
            .andExpect(jsonPath("$.orders").value(0));
    }


    @Test void employeeCannotReadReportsOlderThanThirtyCalendarDays() throws Exception {
        String actor=user(EMPLOYEE_ROLE);
        LocalDate oldest=day.minusDays(29);
        LocalDate tooOld=day.minusDays(30);
        mvc.perform(get("/api/finance/my-closings/"+oldest)
                .with(httpBasic(actor,PASSWORD)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/finance/my-closings/"+tooOld)
                .with(httpBasic(actor,PASSWORD)))
            .andExpect(status().isForbidden());
    }

    @Test void invalidWriteFailsWithoutPersistingAndCatalogIsDenied() throws Exception {
        String actor=user(EMPLOYEE_ROLE);collection(actor,"CASH",100);
        String url="/api/finance/my-closings/"+day;
        mvc.perform(post(url).with(httpBasic(actor,PASSWORD)).with(csrf())
             .contentType(MediaType.APPLICATION_JSON).content(body("0","0","0","0")))
             .andExpect(status().isConflict());
        mvc.perform(post(url).with(httpBasic(actor,PASSWORD))
             .contentType(MediaType.APPLICATION_JSON).content(body("100","0","100","0")))
             .andExpect(status().isForbidden());
        mvc.perform(get(url).with(httpBasic("catalog","integration-test-password")))
             .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM employee_daily_closings WHERE employee_id=?",Integer.class,users.get(0))).isZero();
    }
    @Test void managerOverviewCountsOnlyEmployeesNotManagerOrAdmin() throws Exception {
        String employee=user(EMPLOYEE_ROLE);
        String manager=user(MANAGER_ROLE);
        mvc.perform(get("/api/finance/employee-closings/"+day)
                .with(httpBasic(manager,PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.employees[?(@.username=='"+employee+"')]").isNotEmpty())
            .andExpect(jsonPath("$.employees[?(@.username=='"+manager+"')]").isEmpty());
    }

    @Test void expensesAreCreatorScopedAndFrozenAtPersonalClosing() throws Exception {
        String alice=user(EMPLOYEE_ROLE),bob=user(EMPLOYEE_ROLE),manager=user(MANAGER_ROLE);
        collection(alice,"CASH",1500);
        collection(bob,"BANK",2000);
        expense(alice,"CASH",300);
        expense(bob,"BANK",400);
        expense(manager,"CASH",100);
        String personal="/api/finance/my-closings/"+day;
        mvc.perform(get(personal).with(httpBasic(alice,PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expectedCash").value(1200))
            .andExpect(jsonPath("$.expenseCash").value(300))
            .andExpect(jsonPath("$.expenses.length()").value(1))
            .andExpect(jsonPath("$.expenses[0].paymentMethod").value("CASH"))
            .andExpect(jsonPath("$.expenses[0].amount").value(300));
        mvc.perform(get(personal).with(httpBasic(bob,PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expectedBank").value(1600))
            .andExpect(jsonPath("$.expenses.length()").value(1));
        mvc.perform(get("/api/finance/employee-closings/"+day).with(httpBasic(alice,PASSWORD)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/finance/employee-closings/"+day).with(httpBasic(manager,PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expenses.length()").value(3))
            .andExpect(jsonPath("$.expenseTotal").value(800));
        mvc.perform(post(personal).with(httpBasic(alice,PASSWORD)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("1200","0","1200","0")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.expenses.length()").value(1));
        mvc.perform(get("/api/finance/employee-closings/"+day).with(httpBasic(manager,PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.closedEmployees").value(1))
            .andExpect(jsonPath("$.totalExpectedCash").value(1200))
            .andExpect(jsonPath("$.totalActualCash").value(1200));
        expense(alice,"CASH",50);
        mvc.perform(get(personal).with(httpBasic(alice,PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expenseCash").value(300))
            .andExpect(jsonPath("$.expenses.length()").value(1))
            .andExpect(jsonPath("$.collectionsChangedAfterClose").value(true));
    }

}
