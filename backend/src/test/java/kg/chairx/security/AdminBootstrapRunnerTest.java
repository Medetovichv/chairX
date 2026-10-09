package kg.chairx.security;

import kg.chairx.security.application.AdminBootstrapRunner;
import kg.chairx.security.application.AdminBootstrapService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapRunnerTest {

    private final AdminBootstrapService service =
            mock(AdminBootstrapService.class);

    private final DefaultApplicationArguments args =
            new DefaultApplicationArguments(new String[0]);

    @Test
    void skipsBootstrapWhenConfigurationIsMissing() throws Exception {

        var runner = new AdminBootstrapRunner(
                service,
                "",
                "",
                ""
        );

        runner.run(args);

        verifyNoInteractions(service);
    }

    @Test
    void rejectsIncompleteConfiguration() {

        var runner = new AdminBootstrapRunner(
                service,
                "admin",
                "",
                "Administrator"
        );

        assertThatThrownBy(() -> runner.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Incomplete bootstrap");

        verifyNoInteractions(service);
    }

    @Test
    void initializesAdministratorWhenConfigurationIsComplete()
            throws Exception {

        var runner = new AdminBootstrapRunner(
                service,
                "admin",
                "StrongBootstrapPassword123!",
                "Administrator"
        );

        runner.run(args);

        verify(service).initialize(
                "admin",
                "StrongBootstrapPassword123!",
                "Administrator"
        );

        verifyNoMoreInteractions(service);
    }

    @Test
    void doesNotExposePasswordInConfigurationError() {

        String secret = "SuperSecretPassword123!";

        var runner = new AdminBootstrapRunner(
                service,
                "",
                secret,
                ""
        );

        assertThatThrownBy(() -> runner.run(args))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error ->
                        assertThat(error.getMessage())
                                .doesNotContain(secret)
                );

        verifyNoInteractions(service);
    }
}