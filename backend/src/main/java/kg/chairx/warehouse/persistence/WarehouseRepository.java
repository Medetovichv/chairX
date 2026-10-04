package kg.chairx.warehouse.persistence;

import java.util.UUID;
import kg.chairx.warehouse.domain.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> { }
