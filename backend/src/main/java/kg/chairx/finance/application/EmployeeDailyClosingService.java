package kg.chairx.finance.application;

import kg.chairx.audit.AuditService;
import kg.chairx.finance.domain.FinanceAccount;
import kg.chairx.finance.persistence.FinanceAccountRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Personal receipts and completed sales, never the company's financial closing.
 *
 * Collection attribution: immutable finance_movements (SALE_PAYMENT, PAYMENT)
 * written under the username that accepted the funds, by business_date.
 * Sales attribution: sales.created_by, completion time in Asia/Bishkek.
 * No userId/username is accepted from the browser.
 */
@Service
public class EmployeeDailyClosingService {
    private final JdbcClient jdbc;
    private final FinanceAccountRepository accounts;
    private final AuditService audit;
    private final DailyClosingAccessPolicy policy;

    public EmployeeDailyClosingService(JdbcClient jdbc, FinanceAccountRepository accounts,
                                       AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.audit = audit;
        this.policy = new DailyClosingAccessPolicy(clock);
    }

    public record SaleLine(UUID saleId, String saleNumber, long quantity,
                           BigDecimal total, Instant completedAt,
                           String products,String phone,String address,
                           String fulfillment,String deliveryStatus,String paymentStatus) {
        public SaleLine(UUID id,String number,long quantity,BigDecimal total,Instant completedAt){
            this(id,number,quantity,total,completedAt,null,null,null,null,null,null);
        }
    }
    public record ExpenseLine(UUID expenseId,String category,BigDecimal amount,
                              String paymentMethod,String comment,Instant createdAt,String createdBy) {
        public ExpenseLine(UUID id,String cat,BigDecimal money,String method,String memo,Instant at) {
            this(id,cat,money,method,memo,at,null);
        }
    }
    public record Request(BigDecimal expectedCash, BigDecimal expectedBank,
                          BigDecimal actualCash, BigDecimal actualBank,
                          String cashNote, String bankNote,
                          BigDecimal actualTotal, String totalNote) {
        public Request(BigDecimal expectedCash,BigDecimal expectedBank,
                       BigDecimal actualCash,BigDecimal actualBank,
                       String cashNote,String bankNote) {
            this(expectedCash,expectedBank,actualCash,actualBank,cashNote,bankNote,null,null);
        }
    }
    public record Report(LocalDate businessDate, boolean closed, boolean canClose,
                         BigDecimal expectedCash, BigDecimal expectedBank,
                         BigDecimal actualCash, BigDecimal actualBank,
                         String cashNote, String bankNote, long orders, long chairs,
                         BigDecimal salesValue, List<SaleLine> sales,
                         List<ExpenseLine> expenses, BigDecimal expenseCash,BigDecimal expenseBank,
                         boolean collectionsChangedAfterClose,
                         BigDecimal expectedTotal, BigDecimal actualTotal, String totalNote) {}

    private record Employee(UUID id, String username) {}
    private record Collections(BigDecimal cash, BigDecimal bank) {}

    private Employee employee(String username) {
        return jdbc.sql("""
            SELECT id, username FROM app_users
            WHERE username=:username AND active=TRUE
            """).param("username",username)
            .query((rs,i)->new Employee(rs.getObject("id",UUID.class),rs.getString("username")))
            .optional().orElseThrow(()->new AccessDeniedException("Active employee required"));
    }

    private void validateDate(LocalDate date) {
        if(date==null || date.isAfter(policy.today())) {
            throw new FinanceValidationException("Нельзя выбрать будущую дату");
        }
    }

    private void validateEmployeeHistoryWindow(LocalDate date) {
        if(date.isBefore(policy.today().minusDays(29))) {
            throw new AccessDeniedException("Employee reports are available for the last 30 calendar days only");
        }
    }

    /** Only personal reports are returned; no global balances or other employees' rows. */
    @Transactional(readOnly = true)
    public List<LocalDate> dates(String username) {
        Employee actor=employee(username);
        return jdbc.sql("""
            SELECT business_date FROM employee_daily_closings
            WHERE employee_id=:id AND business_date>=:oldest
            ORDER BY business_date DESC LIMIT 30
            """).param("id",actor.id()).param("oldest",policy.today().minusDays(29))
            .query(LocalDate.class).list();
    }

    @Transactional(readOnly = true)
    public Report get(String username, LocalDate date) {
        validateDate(date);
        validateEmployeeHistoryWindow(date);
        Employee actor=employee(username);
        return result(actor,date);
    }

    private Collections collections(String username, LocalDate date) {
        // Each payment is posted once by FinancePostingService into this journal.
        // Reversals/refunds are distinct manager-controlled events, not money collected.
        List<java.util.Map.Entry<String,BigDecimal>> entries=jdbc.sql("""
            SELECT account_code, COALESCE(SUM(amount),0) AS amount
            FROM finance_movements
            WHERE movement_type='SALE_PAYMENT' AND source_type='PAYMENT'
              AND created_by=:actor AND business_date=:day
            GROUP BY account_code
            """).param("actor",username).param("day",date)
            .query((rs,i)->java.util.Map.entry(rs.getString("account_code"),rs.getBigDecimal("amount")))
            .list();
        BigDecimal cash=BigDecimal.ZERO,bank=BigDecimal.ZERO;
        for(var item:entries) {
            if(item.getKey().equals("CASH"))cash=item.getValue();
            if(item.getKey().equals("BANK"))bank=item.getValue();
        }
        return new Collections(cash,bank);
    }

    private List<ExpenseLine> ownExpenses(String actor,LocalDate day) {
        return jdbc.sql("""
            SELECT id,category,amount,payment_method,comment,created_at FROM expenses
            WHERE created_by=:actor AND expense_date=:day
            ORDER BY created_at,id
            """).param("actor",actor).param("day",day)
            .query((rs,i)->new ExpenseLine(rs.getObject("id",UUID.class),
                rs.getString("category"),rs.getBigDecimal("amount"),
                rs.getString("payment_method"),rs.getString("comment"),
                rs.getTimestamp("created_at").toInstant())).list();
    }

    private List<ExpenseLine> savedExpenses(UUID closingId) {
        return jdbc.sql("""
            SELECT expense_id,category,amount,payment_method,comment,created_at
            FROM employee_daily_closing_expenses
            WHERE closing_id=:id ORDER BY created_at,expense_id
            """).param("id",closingId)
            .query((rs,i)->new ExpenseLine(rs.getObject("expense_id",UUID.class),
                rs.getString("category"),rs.getBigDecimal("amount"),
                rs.getString("payment_method"),rs.getString("comment"),
                rs.getTimestamp("created_at").toInstant())).list();
    }

    private static BigDecimal expenseTotal(List<ExpenseLine> rows,String method){
        return rows.stream().filter(row->method.equals(row.paymentMethod()))
            .map(ExpenseLine::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
    }

    // An expense is attributable only to its creator, not another employee.
    private Collections netCollections(String username,LocalDate day){
        Collections gross=collections(username,day);
        var expenses=ownExpenses(username,day);
        BigDecimal cash=gross.cash().subtract(expenseTotal(expenses,"CASH"));
        BigDecimal bank=gross.bank().subtract(expenseTotal(expenses,"BANK"));
        return new Collections(cash,bank);
    }

    private List<SaleLine> liveSales(String username, LocalDate date) {
        var from=date.atStartOfDay(DailyClosingAccessPolicy.BUSINESS_ZONE).toInstant().atOffset(ZoneOffset.UTC);
        var until=date.plusDays(1).atStartOfDay(DailyClosingAccessPolicy.BUSINESS_ZONE).toInstant().atOffset(ZoneOffset.UTC);
        return jdbc.sql("""
            SELECT s.id,s.sale_number,COALESCE(detail.quantity,0) AS quantity,
                   COALESCE(detail.total,0) AS total,
                   GREATEST(s.fulfilled_at,p.paid_at) AS completed_at,
                   detail.products,COALESCE(d.recipient_phone,c.whatsapp_phone,c.phone) AS phone,
                   COALESCE(d.address,c.address) AS address,
                   s.fulfillment_type,d.status AS delivery_status,'PAID' AS payment_status
            FROM sales s
            LEFT JOIN customers c ON c.id=s.customer_id
            LEFT JOIN deliveries d ON d.sale_id=s.id
            LEFT JOIN LATERAL (
                SELECT SUM(si.quantity) AS quantity,
                       SUM(si.quantity * si.unit_sale_price) AS total,
                       string_agg(pr.name || ' / ' || v.name || ' ×' || si.quantity::text,
                            ', ' ORDER BY pr.name,v.name) AS products
                FROM sale_items si
                JOIN product_variants v ON v.id=si.product_variant_id
                JOIN products pr ON pr.id=v.product_id
                WHERE si.sale_id=s.id
            ) detail ON TRUE
            JOIN LATERAL (
                SELECT SUM(payment.amount) AS amount, MAX(payment.paid_at) AS paid_at
                FROM payments payment
                WHERE payment.sale_id=s.id AND payment.status='PAID'
                HAVING SUM(payment.amount) >= COALESCE(detail.total,0)
            ) p ON TRUE
            WHERE s.status='FULFILLED'
              AND (s.fulfillment_type='SELF_PICKUP'
                   OR (s.fulfillment_type IN ('CITY_DELIVERY','REGION_DELIVERY')
                       AND d.status IN ('IN_TRANSIT','DELIVERED')))
              -- One sale belongs to one business day: the day both physical
              -- handover and full payment have been registered.
              AND GREATEST(s.fulfilled_at,p.paid_at)>=:fromDate
              AND GREATEST(s.fulfilled_at,p.paid_at)<:untilDate
            ORDER BY completed_at,s.id
            """).param("fromDate",from).param("untilDate",until)
            .query((rs,i)->new SaleLine(rs.getObject("id",UUID.class),
                    rs.getString("sale_number"),rs.getLong("quantity"),
                    rs.getBigDecimal("total"),rs.getTimestamp("completed_at").toInstant(),
                    rs.getString("products"),rs.getString("phone"),rs.getString("address"),
                    rs.getString("fulfillment_type"),rs.getString("delivery_status"),
                    rs.getString("payment_status")))
            .list();
    }

    private List<SaleLine> snapshot(UUID closingId) {
        return jdbc.sql("""
            SELECT sale_id,sale_number,quantity,total,completed_at,
                   products,contact_phone AS phone,delivery_address AS address,
                   fulfillment AS fulfillment_type,delivery_status,payment_status
            FROM employee_daily_closing_sales WHERE closing_id=:id
            ORDER BY completed_at,sale_id
            """).param("id",closingId)
            .query((rs,i)->new SaleLine(rs.getObject("sale_id",UUID.class),
                    rs.getString("sale_number"),rs.getLong("quantity"),
                    rs.getBigDecimal("total"),rs.getTimestamp("completed_at").toInstant(),
                    rs.getString("products"),rs.getString("phone"),rs.getString("address"),
                    rs.getString("fulfillment_type"),rs.getString("delivery_status"),
                    rs.getString("payment_status")))
            .list();
    }

    private Report result(Employee actor, LocalDate date) {
        var saved=jdbc.sql("""
            SELECT id,expected_cash,expected_bank,actual_cash,actual_bank,cash_note,bank_note,
                   actual_total,total_note
            FROM employee_daily_closings
            WHERE employee_id=:actor AND business_date=:day
            """).param("actor",actor.id()).param("day",date)
            .query((rs,i)->new Stored(rs.getObject("id",UUID.class),
                    rs.getBigDecimal("expected_cash"),rs.getBigDecimal("expected_bank"),
                    rs.getBigDecimal("actual_cash"),rs.getBigDecimal("actual_bank"),
                    rs.getString("cash_note"),rs.getString("bank_note"),
                rs.getBigDecimal("actual_total"),rs.getString("total_note")))
            .optional().orElse(null);
        Collections now=netCollections(actor.username(),date);
        List<ExpenseLine> personalExpenses=saved==null?ownExpenses(actor.username(),date):savedExpenses(saved.id());
        List<SaleLine> sales=saved==null?liveSales(actor.username(),date):snapshot(saved.id());
        boolean changed=saved!=null && (saved.cash().compareTo(now.cash())!=0
                || saved.bank().compareTo(now.bank())!=0);
        return new Report(date,saved!=null,
                saved==null && policy.normalWriteWindow(date,policy.now()),
                saved==null?now.cash():saved.cash(),
                saved==null?now.bank():saved.bank(),
                saved==null?null:saved.actualCash(),
                saved==null?null:saved.actualBank(),
                saved==null?null:saved.cashNote(),
                saved==null?null:saved.bankNote(),
                sales.size(),sales.stream().mapToLong(SaleLine::quantity).sum(),
                sales.stream().map(SaleLine::total).reduce(BigDecimal.ZERO,BigDecimal::add),
                sales,personalExpenses,expenseTotal(personalExpenses,"CASH"),
                expenseTotal(personalExpenses,"BANK"),changed,
                (saved==null?now.cash().add(now.bank()):saved.cash().add(saved.bank())),
                saved==null?null:saved.actualTotal()!=null?saved.actualTotal():
                    saved.actualCash().add(saved.actualBank()),
                saved==null?null:saved.totalNote());
    }

    private record Stored(UUID id, BigDecimal cash, BigDecimal bank,
                          BigDecimal actualCash, BigDecimal actualBank,
                          String cashNote, String bankNote,
                          BigDecimal actualTotal,String totalNote) {}

    private static void amount(BigDecimal money) {
        if(money==null || money.signum()<0 || money.stripTrailingZeros().scale()>0) {
            throw new FinanceValidationException("Суммы указываются целыми неотрицательными сомами");
        }
    }
    private static String note(String value) {
        if(value!=null && value.length()>2000)throw new FinanceValidationException("Слишком длинное примечание");
        return value==null || value.isBlank()?null:value.strip();
    }

    /**
     * Account row locks serialize against sale payments, with the same
     * BANK -> CASH ordering as the existing corporate daily close.
     * Completion gate protects the sales snapshot; NO ledger posting occurs.
     */
    @Transactional
    public Report close(String username, LocalDate date, Request input) {
        validateDate(date);
        if(input==null)throw new FinanceValidationException("Данные личного закрытия обязательны");
        amount(input.expectedCash());amount(input.expectedBank());
        boolean combined=input.actualTotal()!=null;
        String cashNote=note(input.cashNote()),bankNote=note(input.bankNote());
        String totalNote=note(input.totalNote());
        if(combined) {
            amount(input.actualTotal());
            if(input.actualCash()!=null || input.actualBank()!=null)
                throw new FinanceValidationException("При общей сверке не указывайте разбивку CASH/BANK");
            if(input.actualTotal().compareTo(input.expectedCash().add(input.expectedBank()))!=0
                    && totalNote==null)
                throw new FinanceValidationException("Укажите одну причину расхождения общей суммы");
            cashNote=null;bankNote=null;
        } else {
            amount(input.actualCash());amount(input.actualBank());
            if(input.actualCash().compareTo(input.expectedCash())!=0 && cashNote==null
                    || input.actualBank().compareTo(input.expectedBank())!=0 && bankNote==null)
                throw new FinanceValidationException("Укажите причину расхождения CASH/BANK");
        }

        // Resolve identity server-side. Never accept actor or employee id from request.
        Employee actor=employee(username);
        kg.chairx.common.db.CompletionGuard.acquire(jdbc);
        accounts.lockBalance(FinanceAccount.BANK);
        accounts.lockBalance(FinanceAccount.CASH);
        if(!policy.normalWriteWindow(date,policy.now()))
            throw new FinanceConflictException("Личное закрытие за эту дату недоступно");
        boolean existing=Boolean.TRUE.equals(jdbc.sql("""
            SELECT EXISTS(SELECT 1 FROM employee_daily_closings
                          WHERE employee_id=:id AND business_date=:day)
            """).param("id",actor.id()).param("day",date).query(Boolean.class).single());
        if(existing)throw new FinanceConflictException("Личный день уже закрыт");

        Collections amount=netCollections(actor.username(),date);
        if(amount.cash().signum()<0 || amount.bank().signum()<0)
            throw new FinanceConflictException("Расходы превышают личные поступления. Обратитесь к менеджеру для сверки.");
        if(input.expectedCash().compareTo(amount.cash())!=0
                || input.expectedBank().compareTo(amount.bank())!=0)
            throw new FinanceConflictException("Суммы изменились: обновите расчёт перед закрытием");
        var sales=liveSales(actor.username(),date);
        UUID id=UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO employee_daily_closings
            (id,employee_id,business_date,expected_cash,expected_bank,actual_cash,
             actual_bank,cash_note,bank_note,actual_total,total_note)
            VALUES (:id,:actor,:day,:cash,:bank,:actualCash,:actualBank,:cashNote,:bankNote,:actualTotal,:totalNote)
            """).param("id",id).param("actor",actor.id()).param("day",date)
            .param("cash",amount.cash()).param("bank",amount.bank())
            .param("actualCash",input.actualCash(),java.sql.Types.NUMERIC)
            .param("actualBank",input.actualBank(),java.sql.Types.NUMERIC)
            .param("cashNote",cashNote,java.sql.Types.VARCHAR)
            .param("bankNote",bankNote,java.sql.Types.VARCHAR)
            .param("actualTotal",input.actualTotal(),java.sql.Types.NUMERIC)
            .param("totalNote",totalNote,java.sql.Types.VARCHAR).update();
        for(var sale:sales) {
            jdbc.sql("""
                INSERT INTO employee_daily_closing_sales
                (closing_id,sale_id,sale_number,quantity,total,completed_at,
                 products,contact_phone,delivery_address,fulfillment,delivery_status,payment_status)
                VALUES (:closing,:sale,:number,:quantity,:total,:completedAt,
                        :products,:phone,:address,:fulfillment,:deliveryStatus,:paymentStatus)
                """).param("closing",id).param("sale",sale.saleId())
                .param("number",sale.saleNumber()).param("quantity",sale.quantity())
                .param("total",sale.total())
                .param("completedAt",sale.completedAt().atOffset(ZoneOffset.UTC))
                .param("products",sale.products(),java.sql.Types.VARCHAR)
                .param("phone",sale.phone(),java.sql.Types.VARCHAR)
                .param("address",sale.address(),java.sql.Types.VARCHAR)
                .param("fulfillment",sale.fulfillment(),java.sql.Types.VARCHAR)
                .param("deliveryStatus",sale.deliveryStatus(),java.sql.Types.VARCHAR)
                .param("paymentStatus",sale.paymentStatus(),java.sql.Types.VARCHAR).update();
        }
        for(var expense:ownExpenses(actor.username(),date)){
            jdbc.sql("""
                INSERT INTO employee_daily_closing_expenses
                (closing_id,expense_id,category,amount,payment_method,comment,created_at)
                VALUES (:closing,:expense,:category,:amount,:method,:comment,:created)
                """).param("closing",id).param("expense",expense.expenseId())
                .param("category",expense.category()).param("amount",expense.amount())
                .param("method",expense.paymentMethod())
                .param("comment",expense.comment(),java.sql.Types.VARCHAR)
                .param("created",expense.createdAt().atOffset(ZoneOffset.UTC))
                .update();
        }
        java.util.Map<String,Object> auditDetails=new java.util.LinkedHashMap<>();
        auditDetails.put("businessDate",date.toString());
        auditDetails.put("expectedCash",amount.cash());
        auditDetails.put("expectedBank",amount.bank());
        auditDetails.put("expectedTotal",amount.cash().add(amount.bank()));
        auditDetails.put("saleCount",sales.size());
        if(combined) {
            auditDetails.put("actualTotal",input.actualTotal());
            if(totalNote!=null)auditDetails.put("totalNote",totalNote);
        } else {
            auditDetails.put("actualCash",input.actualCash());
            auditDetails.put("actualBank",input.actualBank());
        }
        audit.recordAs(actor.username(),"EMPLOYEE_DAILY_CLOSING",id,
                "PERSONAL_DAY_CLOSED",null,auditDetails);
        return result(actor,date);
    }
}
