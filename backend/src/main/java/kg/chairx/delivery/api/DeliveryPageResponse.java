package kg.chairx.delivery.api;
import java.util.List;
public record DeliveryPageResponse(List<DeliverySummary> items,int page,int size,long total) {}
