package kg.chairx.security;

import kg.chairx.security.application.ChairxUserDetailsService;
import kg.chairx.security.persistence.AppUserRepository;
import kg.chairx.security.persistence.SecurityRoleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChairxUserDetailsServiceTest {

    private AppUserRepository users;
    private SecurityRoleRepository roles;
    private ChairxUserDetailsService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        roles = mock(SecurityRoleRepository.class);

        service = new ChairxUserDetailsService(users, roles);
    }

    @Test
    void activeUserReceivesPermissions() {
        when(users.findByUsername("manager")).thenReturn(Optional.of(
                new AppUserRepository.AppUserRecord(
                        userId,
                        "manager",
                        "{noop}test-password",
                        "Test Manager",
                        true
                )
        ));

        when(roles.findPermissionsByUserId(userId))
                .thenReturn(Set.of("FINANCE_READ", "SALES_READ"));

        var result = service.loadUserByUsername("manager");

        assertThat(result.isEnabled()).isTrue();

        assertThat(result.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder(
                        "FINANCE_READ",
                        "SALES_READ"
                );
    }

    @Test
    void inactiveUserIsDisabled() {
        when(users.findByUsername("inactive")).thenReturn(Optional.of(
                new AppUserRepository.AppUserRecord(
                        userId,
                        "inactive",
                        "{noop}test-password",
                        "Inactive Employee",
                        false
                )
        ));

        when(roles.findPermissionsByUserId(userId))
                .thenReturn(Set.of("SALES_READ"));

        var result = service.loadUserByUsername("inactive");

        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    void unknownUserIsRejected() {
        when(users.findByUsername("unknown"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.loadUserByUsername("unknown")
        ).isInstanceOf(UsernameNotFoundException.class);

        verifyNoInteractions(roles);
    }

    @Test
    void multipleRolesProvideCombinedPermissions() {
        when(users.findByUsername("employee")).thenReturn(Optional.of(
                new AppUserRepository.AppUserRecord(
                        userId,
                        "employee",
                        "{noop}test-password",
                        "Employee",
                        true
                )
        ));

        when(roles.findPermissionsByUserId(userId))
                .thenReturn(Set.of(
                        "SALES_READ",
                        "SALES_CREATE",
                        "FINANCE_READ"
                ));

        var result = service.loadUserByUsername("employee");

        assertThat(result.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder(
                        "SALES_READ",
                        "SALES_CREATE",
                        "FINANCE_READ"
                );
    }
}