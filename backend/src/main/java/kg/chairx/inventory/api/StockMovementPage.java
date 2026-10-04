package kg.chairx.inventory.api;

import java.util.List;
import kg.chairx.inventory.domain.StockMovement;

public record StockMovementPage(List<StockMovement> items, int page, int size, long totalElements) { }
