package kg.chairx.sale.api;

import java.util.List;

public record SaleHistoryPage(List<SaleHistoryEvent> items, int page, int size, long total) {
}
