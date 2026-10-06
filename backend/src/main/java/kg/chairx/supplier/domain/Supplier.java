package kg.chairx.supplier.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "suppliers")
public class Supplier {
    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 1000)
    private String contactInformation;
    @Column(length = 4000)
    private String comment;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Supplier() { }

    public Supplier(String name, String contactInformation, String comment) {
        id = UUID.randomUUID();
        active = true;

        update(name, contactInformation, comment);
    }

    public void update(String name, String contactInformation, String comment) {
        this.name = name.strip();
        this.contactInformation = normalize(contactInformation);
        this.comment = normalize(comment);
    }

    public void setActive(boolean active) { this.active = active; }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID getId() { return id; }

    public String getName() { return name; }
    public String getContactInformation() { return contactInformation; }
    public String getComment() { return comment; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
