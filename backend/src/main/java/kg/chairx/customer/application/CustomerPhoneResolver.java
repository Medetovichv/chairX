package kg.chairx.customer.application;

import kg.chairx.customer.api.CreateCustomerRequest;
import kg.chairx.customer.api.CustomerResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * One-click phone lookup/create for sales. Shares the advisory lock key
 * with SaleWithDeliveryService to avoid cross-flow duplicate creation.
 * Does not silently merge ambiguous historic contacts.
 */
@Service
public class CustomerPhoneResolver {
    private final JdbcClient jdbc;
    private final CustomerService customers;

    public CustomerPhoneResolver(JdbcClient jdbc,CustomerService customers) {
        this.jdbc=jdbc;this.customers=customers;
    }

    private record Candidate(UUID id, boolean active) {}

    @Transactional
    public CustomerResponse resolve(String phone) {
        String local=KyrgyzPhoneNormalizer.local(phone);
        if(local==null) throw new CustomerRuleViolationException(
                "Укажите корректный номер Кыргызстана (например, +996 700 123456)");

        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))")
                .param("key","chairx-delivery-customer:"+local)
                .query((rs,row)->true).single();

        var matches=jdbc.sql("""
                SELECT DISTINCT id,active FROM customers
                WHERE regexp_replace(coalesce(phone,''),'[^0-9]','','g') IN (:a,:b,:c,:d)
                   OR regexp_replace(coalesce(secondary_phone,''),'[^0-9]','','g') IN (:a,:b,:c,:d)
                   OR regexp_replace(coalesce(whatsapp_phone,''),'[^0-9]','','g') IN (:a,:b,:c,:d)
                ORDER BY id LIMIT 2
                """)
                .param("a",local).param("b","0"+local)
                .param("c","996"+local).param("d","00996"+local)
                .query((rs,row)->new Candidate(rs.getObject("id",UUID.class),
                        rs.getBoolean("active"))).list();
        if(matches.size()>1)throw new CustomerRuleViolationException(
                "Несколько клиентов используют этот номер. Выберите нужную карточку вручную.");
        if(matches.size()==1){
            var match=matches.getFirst();
            if(!match.active())throw new CustomerRuleViolationException(
                    "Клиент с этим номером неактивен. Выберите клиента вручную.");
            return customers.get(match.id());
        }
        return customers.create(new CreateCustomerRequest(
                null,phone.trim(),null,null,null,null,null,null));
    }
}
