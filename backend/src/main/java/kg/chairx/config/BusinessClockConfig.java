package kg.chairx.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Single injectable time source for business rules and deterministic tests. */
@Configuration
public class BusinessClockConfig {
    @Bean
    Clock businessClock() {
        return Clock.systemUTC();
    }
}
