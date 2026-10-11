package kg.chairx.sale.api;

import java.util.List;
public record SaleCommentsPage(List<SaleCommentResponse> items, int page, int size, long total) {}
