package com.wineinventory.authorization.infrastructure.Authorization.sfs.configuration;

import com.wineinventory.authorization.infrastructure.Authorization.sfs.pipeline.BearerAuthorizationRequestFilter;
import com.wineinventory.authorization.infrastructure.hashing.bcrypt.BCryptHashingService;
import com.wineinventory.authorization.infrastructure.tokens.jwt.BearerTokenService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import java.util.List;


/**
 * Web Security Configuration.
 * This class is responsible for configuring the web security.
 * It enables the method security and configures the security filter chain.
 * It includes the authentication manager, the authentication provider, the password encoder and the authentication entry point.
 */
@Configuration
@EnableMethodSecurity
public class WebSecurityConfiguration {

    private final UserDetailsService userDetailsService;

    private final BearerTokenService tokenService;

    private final BCryptHashingService hashingService;

    private final AuthenticationEntryPoint unauthorizedRequestHandler;

    private final List<String> allowedOrigins;

    /**
     * This method creates the Bearer Authorization Request Filter.
     * @return The Bearer Authorization Request Filter
     * @see BearerAuthorizationRequestFilter
     */
    @Bean
    public BearerAuthorizationRequestFilter authorizationRequestFilter() {
        return new BearerAuthorizationRequestFilter(tokenService, userDetailsService);
    }

    /**
     * This method creates the authentication manager.
     * @param authenticationConfiguration The {@link AuthenticationConfiguration} object with the authentication configuration
     * @return The {@link AuthenticationManager} instance from the authentication configuration
     *
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * This method creates the authentication provider.
     * @return The {@link DaoAuthenticationProvider} authentication provider with the user details service and the password encoder
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        var authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(hashingService);
        return authenticationProvider;
    }

    /**
     * This method creates the password encoder.
     * @return The {@link PasswordEncoder} instance with the hashing service
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return hashingService;
    }

    /**
     * This method creates the security filter chain.
     * It also configures the http security.
     *
     * @param http The {@link HttpSecurity} object to configure with the security filter chain
     * @return The {@link SecurityFilterChain} instance with the application http security configuration
     */
    @Bean
    @Order(1)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(
                "/api/v1/**",
                "/api/v1/accounts/**"
        );
        http.cors(configurer -> configurer.configurationSource(request -> {
            var cors = new CorsConfiguration();
            cors.setAllowedOrigins(allowedOrigins);
            cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH"));
            cors.setAllowedHeaders(List.of("*"));
            return cors;
        }));
        http.csrf(csrfConfigurer -> csrfConfigurer.disable())
                .exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(unauthorizedRequestHandler))
                .sessionManagement( customizer -> customizer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        .requestMatchers(
                                "/api/v1/sign-in",
                                "/api/v1/sign-up",
                                "/api/v1/accounts/sign-up",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/api/v1/accounts/by-email",
                                "/api/v1/subscriptions/paypal/return",
                                "/api/v1/subscriptions/paypal/cancel",
                                "/api/v1/subscriptions")
                        .permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/sign-up").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/subscriptions").permitAll()
                        .anyRequest().authenticated());
        http.authenticationProvider(authenticationProvider());
        http.addFilterBefore(authorizationRequestFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();

    }

    /**
     * This is the constructor of the class.
     * @param userDetailsService The user details service
     * @param tokenService The token service
     * @param hashingService The hashing service
     * @param authenticationEntryPoint The authentication entry point
     * @param allowedOrigins The origins allowed by CORS, from the cors.allowed-origins property
     */
    public WebSecurityConfiguration(@Qualifier("defaultUserDetailsService") UserDetailsService userDetailsService, BearerTokenService tokenService, BCryptHashingService hashingService, AuthenticationEntryPoint authenticationEntryPoint,
                                    @Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
        this.userDetailsService = userDetailsService;
        this.tokenService = tokenService;
        this.hashingService = hashingService;
        this.unauthorizedRequestHandler = authenticationEntryPoint;
    }
}
