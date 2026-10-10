package kg.chairx.security;

import kg.chairx.security.application.LocalDevUsersRunner;
import kg.chairx.security.application.LocalDevUsersService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.*;

class LocalDevUsersRunnerTest {
    @Test
    void delegatesConfigurationWithoutLoggingPasswords() {
        var users = mock(LocalDevUsersService.class);
        var runner = new LocalDevUsersRunner(
                users, "admin", "StrongManagerPassword123!", "StrongEmployeePassword123!");
        runner.run(new DefaultApplicationArguments(new String[0]));
        verify(users).ensureUsers("admin", "StrongManagerPassword123!", "StrongEmployeePassword123!");
        verifyNoMoreInteractions(users);
    }
}

