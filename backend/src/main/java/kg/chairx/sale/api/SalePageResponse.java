package kg.chairx.sale.api;

import java.util.List;
public record SalePageResponse(List<SaleSummary> items,int page,int size,long total) {}
