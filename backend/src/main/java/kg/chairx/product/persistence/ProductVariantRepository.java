package kg.chairx.product.persistence;

import java.util.UUID;
import kg.chairx.product.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    Page<ProductVariant> findByProduct_Id(UUID productId, Pageable pageable);
}
