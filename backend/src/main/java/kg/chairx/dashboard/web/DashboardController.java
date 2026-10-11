package kg.chairx.dashboard.web;

import kg.chairx.dashboard.api.DashboardOverview;
import kg.chairx.dashboard.application.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public DashboardOverview overview() {
        return service.overview();
    }
}
