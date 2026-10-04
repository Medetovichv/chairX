package kg.chairx.product.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "products")
public class Product {
    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 4000)
    private String description;
    @Column(length = 120)
    private String category;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Product() { }

    public Product(String name, String description, String category) {
        id = UUID.randomUUID();
        active = true;

        update(name, description, category);
    }

    public void update(String name, String description, String category) {
        this.name = name.strip();
        this.description = normalize(description);
        this.category = normalize(category);
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
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
