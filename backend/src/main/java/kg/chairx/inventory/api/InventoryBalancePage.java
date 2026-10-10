package kg.chairx.inventory.api;
import kg.chairx.inventory.domain.InventoryBalance;
import java.util.List;
public record InventoryBalancePage(List<InventoryBalance> items,int page,int size,long total) {}
