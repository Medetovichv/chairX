package kg.chairx.security.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Never registered outside the explicit local profile AND opt-in flag.
 * Runs after the existing AdminBootstrapRunner (@Order(0)).
 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "CHAIRX_DEV_SEED_USERS", havingValue = "true")
@Order(10)
public class LocalDevUsersRunner implements ApplicationRunner {
    private final LocalDevUsersService users;
    private final String adminUsername;
    private final String managerPassword;
    private final String employeePassword;

    public LocalDevUsersRunner(
            LocalDevUsersService users,
            @Value("${CHAIRX_BOOTSTRAP_ADMIN_USERNAME:admin}") String adminUsername,
            @Value("${CHAIRX_DEV_MANAGER_PASSWORD:}") String managerPassword,
            @Value("${CHAIRX_DEV_EMPLOYEE_PASSWORD:}") String employeePassword
    ) {
        this.users = users;
        this.adminUsername = adminUsername;
        this.managerPassword = managerPassword;
        this.employeePassword = employeePassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        users.ensureUsers(adminUsername, managerPassword, employeePassword);
    }
}

