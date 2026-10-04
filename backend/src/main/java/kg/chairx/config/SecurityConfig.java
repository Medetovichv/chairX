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

@Configuration
public class SecurityConfig {
    @Bean
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
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper mapper) throws Exception {
        AuthenticationEntryPoint authenticationRequired = (request, response, exception) -> {
            response.setHeader("WWW-Authenticate", "Basic realm=\"ChairX\"");
            writeError(response, mapper, 401, "AUTHENTICATION_REQUIRED", "Требуется авторизация");
        };
        // Basic authentication can be sent automatically by browsers, so CSRF stays enabled.
        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(authenticationRequired))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(authenticationRequired)
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, mapper, 403, "ACCESS_DENIED",
                                        "Доступ запрещён или отсутствует корректный CSRF-токен")));
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
