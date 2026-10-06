package kg.chairx.customer.persistence;

import kg.chairx.customer.domain.Customer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class CustomerRepository {

    private final JdbcClient jdbc;

    public CustomerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Customer customer) {
        jdbc.sql("""
                INSERT INTO customers(
                    id,
                    full_name,
                    phone,
                    secondary_phone,
                    whatsapp_phone,
                    instagram_username,
                    address,
                    city_region,
                    comment,
                    active,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :fullName,
                    :phone,
                    :secondaryPhone,
                    :whatsappPhone,
                    :instagramUsername,
                    :address,
                    :cityRegion,
                    :comment,
                    :active,
                    :createdAt,
                    :updatedAt
                )
                """)
                .param("id", customer.id())
                .param("fullName", customer.fullName())
                .param("phone", customer.phone(), Types.VARCHAR)
                .param("secondaryPhone", customer.secondaryPhone(), Types.VARCHAR)
                .param("whatsappPhone", customer.whatsappPhone(), Types.VARCHAR)
                .param("instagramUsername", customer.instagramUsername(), Types.VARCHAR)
                .param("address", customer.address(), Types.VARCHAR)
                .param("cityRegion", customer.cityRegion(), Types.VARCHAR)
                .param("comment", customer.comment(), Types.VARCHAR)
                .param("active", customer.active())
                .param(
                        "createdAt",
                        customer.createdAt().atOffset(ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .param(
                        "updatedAt",
                        customer.updatedAt().atOffset(ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .update();
    }

    public Optional<Customer> find(UUID id) {
        return jdbc.sql("""
                SELECT *
                FROM customers
                WHERE id = :id
                """)
                .param("id", id)
                .query(CustomerRepository::map)
                .optional();
    }

    public Optional<Customer> lock(UUID id) {
        return jdbc.sql("""
                SELECT *
                FROM customers
                WHERE id = :id
                FOR UPDATE
                """)
                .param("id", id)
                .query(CustomerRepository::map)
                .optional();
    }

    public void update(Customer customer) {
        jdbc.sql("""
                UPDATE customers
                SET
                    full_name = :fullName,
                    phone = :phone,
                    secondary_phone = :secondaryPhone,
                    whatsapp_phone = :whatsappPhone,
                    instagram_username = :instagramUsername,
                    address = :address,
                    city_region = :cityRegion,
                    comment = :comment,
                    active = :active,
                    updated_at = :updatedAt
                WHERE id = :id
                """)
                .param("id", customer.id())
                .param("fullName", customer.fullName())
                .param("phone", customer.phone(), Types.VARCHAR)
                .param("secondaryPhone", customer.secondaryPhone(), Types.VARCHAR)
                .param("whatsappPhone", customer.whatsappPhone(), Types.VARCHAR)
                .param("instagramUsername", customer.instagramUsername(), Types.VARCHAR)
                .param("address", customer.address(), Types.VARCHAR)
                .param("cityRegion", customer.cityRegion(), Types.VARCHAR)
                .param("comment", customer.comment(), Types.VARCHAR)
                .param("active", customer.active())
                .param(
                        "updatedAt",
                        customer.updatedAt().atOffset(ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .update();
    }

    public List<Customer> list(
            int page,
            int size
    ) {
        return jdbc.sql("""
                SELECT *
                FROM customers
                ORDER BY full_name, id
                LIMIT :size
                OFFSET :offset
                """)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(CustomerRepository::map)
                .list();
    }

    public List<Customer> search(
            String query,
            int limit
    ) {
        return jdbc.sql("""
                SELECT *
                FROM customers
                WHERE
                    lower(full_name) LIKE :query
                    OR lower(coalesce(phone, '')) LIKE :query
                    OR lower(coalesce(secondary_phone, '')) LIKE :query
                    OR lower(coalesce(whatsapp_phone, '')) LIKE :query
                    OR lower(coalesce(instagram_username, '')) LIKE :query
                ORDER BY
                    active DESC,
                    full_name,
                    id
                LIMIT :limit
                """)
                .param(
                        "query",
                        "%" + query.toLowerCase() + "%"
                )
                .param("limit", limit)
                .query(CustomerRepository::map)
                .list();
    }

    public long count() {
        return jdbc.sql("""
                SELECT count(*)
                FROM customers
                """)
                .query(Long.class)
                .single();
    }

    private static Customer map(
            ResultSet rs,
            int rowNum
    ) throws SQLException {
        return new Customer(
                rs.getObject("id", UUID.class),
                rs.getString("full_name"),
                rs.getString("phone"),
                rs.getString("secondary_phone"),
                rs.getString("whatsapp_phone"),
                rs.getString("instagram_username"),
                rs.getString("address"),
                rs.getString("city_region"),
                rs.getString("comment"),
                rs.getBoolean("active"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private static Instant instant(
            ResultSet rs,
            String column
    ) throws SQLException {
        var timestamp = rs.getTimestamp(column);

        return timestamp == null
                ? null
                : timestamp.toInstant();
    }
}