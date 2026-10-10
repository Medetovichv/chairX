package kg.chairx.customer.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Customer/contact list with operational sales summary, no full sale hydration. */
public record CustomerOverviewPage(List<Line> items, int page, int size, long total) {
    public record Line(UUID id, String fullName, String phone, String whatsappPhone,
                       String cityRegion, long orderCount, String lastSaleNumber,
                       Instant lastOrderAt) {
    }
}
