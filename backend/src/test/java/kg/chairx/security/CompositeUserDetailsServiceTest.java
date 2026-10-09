package kg.chairx.security;

import kg.chairx.security.application.CompositeUserDetailsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompositeUserDetailsServiceTest {

    private UserDetailsService catalogUsers;
    private UserDetailsService employeeUsers;
    private CompositeUserDetailsService service;

    @BeforeEach
    void setUp() {
        catalogUsers = mock(UserDetailsService.class);
        employeeUsers = mock(UserDetailsService.class);

        service = new CompositeUserDetailsService(
                catalogUsers,
                employeeUsers,
                "catalog"
        );
    }

    @Test
    void catalogUserUsesCatalogAuthentication() {
        var catalog = User.withUsername("catalog")
                .password("{noop}test-password")
                .authorities("CATALOG_ACCESS")
                .build();

        when(catalogUsers.loadUserByUsername("catalog"))
                .thenReturn(catalog);

        var result = service.loadUserByUsername("catalog");

        assertThat(result.getUsername()).isEqualTo("catalog");

        assertThat(result.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("CATALOG_ACCESS");

        verify(catalogUsers).loadUserByUsername("catalog");
        verifyNoInteractions(employeeUsers);
    }

    @Test
    void employeeUsesDatabaseAuthentication() {
        var employee = User.withUsername("manager")
                .password("{noop}test-password")
                .authorities("FINANCE_READ", "SALES_READ")
                .build();

        when(employeeUsers.loadUserByUsername("manager"))
                .thenReturn(employee);

        var result = service.loadUserByUsername("manager");

        assertThat(result.getUsername()).isEqualTo("manager");

        assertThat(result.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder(
                        "FINANCE_READ",
                        "SALES_READ"
                );

        verify(employeeUsers).loadUserByUsername("manager");
        verifyNoInteractions(catalogUsers);
    }

    @Test
    void reservedCatalogUsernameNeverUsesEmployeeAuthentication() {
        when(catalogUsers.loadUserByUsername("catalog"))
                .thenThrow(new UsernameNotFoundException("Catalog not found"));

        assertThatThrownBy(() -> service.loadUserByUsername("catalog"))
                .isInstanceOf(UsernameNotFoundException.class);

        verifyNoInteractions(employeeUsers);
    }

    @Test
    void unknownEmployeeIsRejected() {
        when(employeeUsers.loadUserByUsername("unknown"))
                .thenThrow(new UsernameNotFoundException("User not found"));

        assertThatThrownBy(() -> service.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class);

        verifyNoInteractions(catalogUsers);
    }

    @Test
    void catalogAndEmployeeRemainIndependent() {
        var catalog = User.withUsername("catalog")
                .password("{noop}catalog-password")
                .authorities("CATALOG_ACCESS")
                .build();

        var manager = User.withUsername("manager")
                .password("{noop}manager-password")
                .authorities("FINANCE_READ")
                .build();

        when(catalogUsers.loadUserByUsername("catalog"))
                .thenReturn(catalog);

        when(employeeUsers.loadUserByUsername("manager"))
                .thenReturn(manager);

        var catalogResult = service.loadUserByUsername("catalog");
        var managerResult = service.loadUserByUsername("manager");

        assertThat(catalogResult.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("CATALOG_ACCESS");

        assertThat(managerResult.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("FINANCE_READ");
    }
}