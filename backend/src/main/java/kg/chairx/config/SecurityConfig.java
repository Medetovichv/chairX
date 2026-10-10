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
                        // Only known routes have grants. Unrecognized methods and
                        // new API routes are denied until explicitly reviewed.
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/csrf")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/*", "/api/product-variants/*", "/api/products/*/variants")
                        .hasAnyAuthority("CATALOG_READ", "CATALOG_ACCESS")
                        .requestMatchers(HttpMethod.POST, "/api/products", "/api/products/*/variants", "/api/products/*/activate", "/api/products/*/deactivate", "/api/product-variants/*/activate", "/api/product-variants/*/deactivate")
                        .hasAuthority("CATALOG_MANAGE")
                        .requestMatchers(HttpMethod.PUT, "/api/products/*", "/api/product-variants/*")
                        .hasAuthority("CATALOG_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/suppliers", "/api/suppliers/*")
                        .hasAuthority("SUPPLIERS_READ")
                        .requestMatchers(HttpMethod.POST, "/api/suppliers", "/api/suppliers/*/activate", "/api/suppliers/*/deactivate")
                        .hasAuthority("SUPPLIERS_MANAGE")
                        .requestMatchers(HttpMethod.PUT, "/api/suppliers/*")
                        .hasAuthority("SUPPLIERS_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/customers", "/api/customers/search", "/api/customers/*")
                        .hasAuthority("CUSTOMERS_READ")
                        .requestMatchers(HttpMethod.POST, "/api/customers")
                        .hasAuthority("CUSTOMERS_CREATE")
                        .requestMatchers(HttpMethod.POST, "/api/customers/*/activate", "/api/customers/*/deactivate")
                        .hasAuthority("CUSTOMERS_MANAGE")
                        .requestMatchers(HttpMethod.PUT, "/api/customers/*")
                        .hasAuthority("CUSTOMERS_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/warehouses", "/api/warehouses/*")
                        .hasAuthority("INVENTORY_READ")
                        .requestMatchers(HttpMethod.POST, "/api/warehouses", "/api/warehouses/*/activate", "/api/warehouses/*/deactivate")
                        .hasAuthority("WAREHOUSES_MANAGE")
                        .requestMatchers(HttpMethod.PUT, "/api/warehouses/*")
                        .hasAuthority("WAREHOUSES_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/purchases/*/payments")
                        .hasAuthority("PURCHASE_PAYMENTS_READ")
                        .requestMatchers(HttpMethod.POST, "/api/purchases/*/payments")
                        .hasAuthority("PURCHASE_PAYMENTS_CREATE")
                        .requestMatchers(HttpMethod.POST, "/api/purchases/*/receipts")
                        .hasAuthority("INVENTORY_RECEIVE")
                        .requestMatchers(HttpMethod.GET, "/api/purchases", "/api/purchases/*", "/api/purchases/*/receipts", "/api/purchases/*/receipts/*")
                        .hasAuthority("PURCHASE_READ")
                        .requestMatchers(HttpMethod.POST, "/api/purchases")
                        .hasAuthority("PURCHASE_CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/purchases/*", "/api/purchases/*/cargo")
                        .hasAuthority("PURCHASE_UPDATE")
                        .requestMatchers(HttpMethod.POST, "/api/purchases/*/confirm")
                        .hasAuthority("PURCHASE_CONFIRM")
                        .requestMatchers(HttpMethod.POST, "/api/purchases/*/cancel")
                        .hasAuthority("PURCHASE_CANCEL")
                        .requestMatchers(HttpMethod.GET, "/api/inventory/balances", "/api/inventory/balances/*/*", "/api/inventory/movements", "/api/inventory/transfers", "/api/inventory/transfers/*")
                        .hasAuthority("INVENTORY_READ")
                        .requestMatchers(HttpMethod.POST, "/api/inventory/transfers")
                        .hasAuthority("INVENTORY_TRANSFER")
                        .requestMatchers(HttpMethod.GET, "/api/sales", "/api/sales/*")
                        .hasAuthority("SALES_READ")
                        .requestMatchers(HttpMethod.POST, "/api/sales")
                        .hasAuthority("SALES_CREATE")
                        .requestMatchers(HttpMethod.POST, "/api/sales/*/fulfill")
                        .hasAuthority("SALES_UPDATE")
                        .requestMatchers(HttpMethod.POST, "/api/sales/*/cancel")
                        .hasAuthority("SALES_CANCEL")
                        .requestMatchers(HttpMethod.GET, "/api/deliveries", "/api/deliveries/*")
                        .hasAuthority("DELIVERIES_READ")
                        .requestMatchers(HttpMethod.POST, "/api/deliveries", "/api/deliveries/*/dispatch", "/api/deliveries/*/deliver", "/api/deliveries/*/fail", "/api/deliveries/*/return-to-warehouse")
                        .hasAuthority("DELIVERIES_MANAGE")
                        .requestMatchers(HttpMethod.POST, "/api/deliveries/*/cancel")
                        .hasAuthority("SALES_CANCEL")
                        .requestMatchers(HttpMethod.GET, "/api/returns", "/api/returns/*")
                        .hasAuthority("RETURNS_READ")
                        .requestMatchers(HttpMethod.POST, "/api/returns")
                        .hasAuthority("RETURNS_CREATE")
                        .requestMatchers(HttpMethod.GET, "/api/payments/*", "/api/payments/sale/*", "/api/payments/sale/*/active")
                        .hasAuthority("PAYMENTS_READ")
                        .requestMatchers(HttpMethod.POST, "/api/payments")
                        .hasAuthority("PAYMENTS_CREATE")
                        .requestMatchers(HttpMethod.POST, "/api/payments/*/cancel")
                        .hasAuthority("PAYMENTS_CANCEL")
                        .requestMatchers(HttpMethod.GET, "/api/refunds/*", "/api/refunds/sale/*")
                        .hasAuthority("REFUNDS_READ")
                        .requestMatchers(HttpMethod.POST, "/api/refunds")
                        .hasAuthority("REFUNDS_CREATE")
                        .requestMatchers(HttpMethod.GET, "/api/exchanges/*", "/api/exchanges/*/settlements")
                        .hasAuthority("EXCHANGES_READ")
                        .requestMatchers(HttpMethod.POST, "/api/exchanges")
                        .hasAuthority("EXCHANGES_CREATE")
                        .requestMatchers(HttpMethod.POST, "/api/exchanges/*/settlements")
                        .hasAuthority("EXCHANGES_SETTLE")
                        .requestMatchers(HttpMethod.GET, "/api/expenses", "/api/expenses/*", "/api/expenses/total")
                        .hasAuthority("EXPENSES_READ")
                        .requestMatchers(HttpMethod.POST, "/api/expenses")
                        .hasAuthority("EXPENSES_CREATE")
                        .requestMatchers(HttpMethod.GET, "/api/defects", "/api/defects/*")
                        .hasAuthority("DEFECTS_READ")
                        .requestMatchers(HttpMethod.POST, "/api/defects", "/api/defects/*/wait-for-parts", "/api/defects/*/resolve")
                        .hasAuthority("DEFECTS_MANAGE")
                        .requestMatchers(HttpMethod.POST, "/api/defects/*/write-off")
                        .hasAuthority("DEFECTS_WRITE_OFF")
                        .requestMatchers(HttpMethod.GET, "/api/finance/accounts", "/api/finance/cash-flow", "/api/finance/closings", "/api/finance/closings/*")
                        .hasAuthority("FINANCE_READ")
                        .requestMatchers(HttpMethod.POST, "/api/finance/transfers")
                        .hasAuthority("FINANCE_TRANSFER")
                        .requestMatchers(HttpMethod.POST, "/api/finance/opening-balances")
                        .hasAuthority("FINANCE_INITIALIZE")
                        .requestMatchers(HttpMethod.POST, "/api/finance/closings/*")
                        .hasAuthority("FINANCE_CLOSE")
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
                        .anyRequest().denyAll()
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
