package kg.chairx.inventory.api;
import java.util.List;
public record InventoryBalancePage(List<InventoryBalanceResponse> items,int page,int size,long total) {}
