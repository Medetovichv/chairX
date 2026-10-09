package kg.chairx.security.application;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class CompositeUserDetailsService implements UserDetailsService {

    private final UserDetailsService catalogUsers;
    private final UserDetailsService employeeUsers;
    private final String catalogUsername;

    public CompositeUserDetailsService(
            UserDetailsService catalogUsers,
            UserDetailsService employeeUsers,
            String catalogUsername
    ) {
        this.catalogUsers = catalogUsers;
        this.employeeUsers = employeeUsers;
        this.catalogUsername = catalogUsername;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        if (catalogUsername.equals(username)) {
            return catalogUsers.loadUserByUsername(username);
        }

        return employeeUsers.loadUserByUsername(username);
    }
}