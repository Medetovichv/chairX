package kg.chairx.product.persistence;

import java.util.UUID;
import kg.chairx.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** SQL-level filtering remains server-paginated; never filter a partial page in React. */
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {}
