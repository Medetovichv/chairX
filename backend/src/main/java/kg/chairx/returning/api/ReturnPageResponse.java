package kg.chairx.returning.api;
import java.util.List;
public record ReturnPageResponse(List<ReturnSummary> items,int page,int size,long total) {}
