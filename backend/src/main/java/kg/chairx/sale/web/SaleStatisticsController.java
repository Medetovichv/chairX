package kg.chairx.sale.web;

import kg.chairx.sale.api.SaleStatistics;
import kg.chairx.sale.application.SaleStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
public class SaleStatisticsController {
    private final SaleStatisticsService service;

    public SaleStatisticsController(SaleStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/statistics")
    public SaleStatistics statistics() {
        return service.last30Days();
    }
}
