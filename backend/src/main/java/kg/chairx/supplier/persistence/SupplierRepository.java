package kg.chairx.supplier.persistence;

import java.util.UUID;
import kg.chairx.supplier.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;


public interface SupplierRepository extends JpaRepository<Supplier, UUID> {}
