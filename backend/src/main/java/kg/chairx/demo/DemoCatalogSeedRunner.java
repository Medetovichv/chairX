package kg.chairx.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/** Opt-in twice: local Spring profile and explicit demo data flag. */
@Component
@Profile("local")
@ConditionalOnProperty(name="CHAIRX_DEMO_DATA_ENABLED", havingValue="true")
@Order(20)
public class DemoCatalogSeedRunner implements ApplicationRunner {
    private final DemoCatalogSeedService seeds;
    public DemoCatalogSeedRunner(DemoCatalogSeedService seeds) {
        this.seeds = seeds;
    }
    @Override public void run(ApplicationArguments arguments) {
        var original = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(
                            "chairx-demo-bootstrap", null, List.of()));
            seeds.seed();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(original);
        }
    }
}
