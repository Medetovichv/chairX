package kg.chairx.customer.application;

import kg.chairx.customer.persistence.CustomerRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerAddressService {
    private final JdbcClient jdbc;
    private final CustomerRepository customers;
    public CustomerAddressService(JdbcClient jdbc, CustomerRepository customers) {
        this.jdbc=jdbc;
        this.customers=customers;
    }
    public record Address(UUID id, UUID customerId, String address, String cityRegion, Instant createdAt) {}
    public record AddressInput(String address, String cityRegion) {}

    @Transactional(readOnly=true)
    public List<Address> list(UUID customerId) {
        customers.find(customerId).orElseThrow(()->new CustomerNotFoundException(customerId));
        return jdbc.sql("SELECT id,customer_id,address,city_region,created_at FROM customer_addresses WHERE customer_id=:id ORDER BY created_at,id")
                .param("id",customerId)
                .query((rs,n)->new Address(rs.getObject("id",UUID.class),
                        rs.getObject("customer_id",UUID.class),rs.getString("address"),
                        rs.getString("city_region"),rs.getTimestamp("created_at").toInstant())).list();
    }

    @Transactional
    public Address add(UUID customerId, AddressInput input) {
        customers.lock(customerId).orElseThrow(()->new CustomerNotFoundException(customerId));
        if(input==null||input.address()==null||input.address().isBlank()||input.address().trim().length()>500
                ||(input.cityRegion()!=null&&input.cityRegion().length()>200))
            throw new CustomerRuleViolationException("Укажите корректный адрес доставки");
        String address=input.address().trim();
        String city=input.cityRegion()==null||input.cityRegion().isBlank()?null:input.cityRegion().trim();
        var existing=list(customerId).stream().filter(a->a.address().equalsIgnoreCase(address)
                &&java.util.Objects.equals(a.cityRegion(),city)).findFirst();
        if(existing.isPresent())return existing.get();
        UUID id=UUID.randomUUID();
        return jdbc.sql("""
                INSERT INTO customer_addresses(id,customer_id,address,city_region)
                VALUES (:id,:customer,:address,:city)
                RETURNING id,customer_id,address,city_region,created_at
                """).param("id",id).param("customer",customerId)
                .param("address",address).param("city",city,java.sql.Types.VARCHAR)
                .query((rs,n)->new Address(rs.getObject("id",UUID.class),
                        rs.getObject("customer_id",UUID.class),rs.getString("address"),
                        rs.getString("city_region"),rs.getTimestamp("created_at").toInstant())).single();
    }
}
