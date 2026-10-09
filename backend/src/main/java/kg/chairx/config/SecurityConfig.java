package kg.chairx.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import kg.chairx.common.web.ApiError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;
import kg.chairx.security.application.ChairxUserDetailsService;
import kg.chairx.security.application.CompositeUserDetailsService;
import kg.chairx.security.persistence.AppUserRepository;
import kg.chairx.security.persistence.SecurityRoleRepository;
import org.springframework.http.HttpMethod;

@Configuration
public class SecurityConfig {

    UserDetailsService catalogUser(@Value("${CHAIRX_CATALOG_USERNAME:catalog}") String username,
            @Value("${CHAIRX_CATALOG_PASSWORD}") String password) {
        if (username.isBlank() || username.length() > 200 || password.isBlank()) {
            throw new IllegalArgumentException("Catalog credentials must be configured");
        }
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(encoder.encode(password)).authorities("CATALOG_ACCESS").build());
    }

    @Bean
    UserDetailsService userDetailsService(
            @Value("${CHAIRX_CATALOG_USERNAME:catalog}") String catalogUsername,
            @Value("${CHAIRX_CATALOG_PASSWORD}") String catalogPassword,
            AppUserRepository users,
            SecurityRoleRepository roles
    ) {
        UserDetailsService catalog = catalogUser(
                catalogUsername,
                catalogPassword
        );

        UserDetailsService employees =
                new ChairxUserDetailsService(users, roles);

        return new CompositeUserDetailsService(
                catalog,
                employees,
                catalogUsername
        );
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper mapper) throws Exception {

        AuthenticationEntryPoint authenticationRequired = (request, response, exception) -> {
            response.setHeader("WWW-Authenticate", "Basic realm=\"ChairX\"");
            writeError(response, mapper, 401, "AUTHENTICATION_REQUIRED", "Требуется авторизация");
        };

        http
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(HttpMethod.POST, "/api/finance/closings/*")
                        .hasAuthority("FINANCE_CLOSE")

                        // Просмотр финансов
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/finance/**"
                        ).hasAuthority("FINANCE_READ")

                        // Переводы между счетами
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/finance/transfers"
                        ).hasAuthority("FINANCE_TRANSFER")

                        // Установка начальных остатков
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/finance/opening-balances"
                        ).hasAuthority("FINANCE_INITIALIZE")

                        // Остальные финансовые маршруты запрещены
                        .requestMatchers("/api/finance/**").denyAll()

                        // Разрешения для административного API
                        .requestMatchers(HttpMethod.GET, "/api/admin/users", "/api/admin/users/*")
                        .hasAuthority("USERS_READ")
                        .requestMatchers(HttpMethod.POST, "/api/admin/users")
                        .hasAuthority("USERS_CREATE")
                        .requestMatchers(HttpMethod.PATCH, "/api/admin/users/*/deactivate")
                        .hasAuthority("USERS_DEACTIVATE")
                        .requestMatchers(HttpMethod.GET, "/api/admin/roles")
                        .hasAuthority("ROLES_READ")
                        .requestMatchers(HttpMethod.POST, "/api/admin/users/*/roles")
                        .hasAuthority("ROLES_ASSIGN")
                        .requestMatchers(HttpMethod.DELETE, "/api/admin/users/*/roles/*")
                        .hasAuthority("ROLES_ASSIGN")
                        .requestMatchers("/api/admin/**").denyAll()
                        .anyRequest().authenticated()
                )

                // Сохраняем HTTP Basic
                .httpBasic(basic ->
                        basic.authenticationEntryPoint(authenticationRequired)
                )

                // Сохраняем обработку ошибок
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationRequired)
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(
                                        response,
                                        mapper,
                                        403,
                                        "ACCESS_DENIED",
                                        "Доступ запрещён или отсутствует корректный CSRF-токен"
                                )
                        )
                );

        return http.build();
    }

    private static void writeError(HttpServletResponse response, JsonMapper mapper,
            int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getWriter(), ApiError.of(code, message));
    }
}
