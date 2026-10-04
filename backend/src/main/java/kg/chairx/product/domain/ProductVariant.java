package kg.chairx.product.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
public class ProductVariant {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 100)
    private String sku;
    @Column(length = 100)
    private String color;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal recommendedSalePrice;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected ProductVariant() { }

    public ProductVariant(Product product, String name, String sku, String color, BigDecimal recommendedSalePrice) {
        id = UUID.randomUUID();
        active = true;
        this.product = product;
        update(name, sku, color, recommendedSalePrice);
    }

    public void update(String name, String sku, String color, BigDecimal recommendedSalePrice) {
        this.name = name.strip();
        this.sku = normalize(sku);
        this.color = normalize(color);
        this.recommendedSalePrice = recommendedSalePrice;
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
    public UUID getProductId() { return product.getId(); }
    public String getName() { return name; }
    public String getSku() { return sku; }
    public String getColor() { return color; }
    public BigDecimal getRecommendedSalePrice() { return recommendedSalePrice; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
