package kg.chairx.sale.api;

import java.time.Instant;
import java.util.UUID;

public record SaleCommentResponse(UUID id, UUID saleId, String author, String text, Instant createdAt) {}
