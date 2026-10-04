package kg.chairx.product.persistence;

import java.util.UUID;
import kg.chairx.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;


public interface ProductRepository extends JpaRepository<Product, UUID> {}
