package kg.chairx.security.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapService bootstrapService;

    private final String username;
    private final String password;
    private final String displayName;

    public AdminBootstrapRunner(
            AdminBootstrapService bootstrapService,
            @Value("${CHAIRX_BOOTSTRAP_ADMIN_USERNAME:}") String username,
            @Value("${CHAIRX_BOOTSTRAP_ADMIN_PASSWORD:}") String password,
            @Value("${CHAIRX_BOOTSTRAP_ADMIN_DISPLAY_NAME:}") String displayName
    ) {
        this.bootstrapService = bootstrapService;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) {

        boolean hasUsername = username != null && !username.isBlank();
        boolean hasPassword = password != null && !password.isBlank();
        boolean hasDisplayName = displayName != null && !displayName.isBlank();

        // Без конфигурации автоматический bootstrap не выполняется.
        if (!hasUsername && !hasPassword && !hasDisplayName) {
            return;
        }

        // Частичная конфигурация считается ошибкой.
        if (!hasUsername || !hasPassword || !hasDisplayName) {
            throw new IllegalStateException(
                    "Incomplete bootstrap administrator configuration"
            );
        }

        bootstrapService.initialize(username, password, displayName);
    }
}

