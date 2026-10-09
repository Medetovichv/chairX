package kg.chairx.security.application;

import kg.chairx.security.persistence.AppUserRepository;
import kg.chairx.security.persistence.SecurityRoleRepository;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

public class ChairxUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;
    private final SecurityRoleRepository roles;

    public ChairxUserDetailsService(
            AppUserRepository users,
            SecurityRoleRepository roles
    ) {
        this.users = users;
        this.roles = roles;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        var employee = users.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found")
                );

        var permissions = roles.findPermissionsByUserId(employee.id());

        var authorities = permissions.stream()
                .map(permission -> (org.springframework.security.core.GrantedAuthority)
                        () -> permission)
                .toList();

        return User.withUsername(employee.username())
                .password(employee.passwordHash())
                .disabled(!employee.active())
                .authorities(authorities)
                .build();
    }
}