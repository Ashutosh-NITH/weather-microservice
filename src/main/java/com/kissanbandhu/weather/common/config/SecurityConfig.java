package com.kissanbandhu.weather.common.config;

import com.kissanbandhu.common.security.JwtAuthFilter;
import com.kissanbandhu.common.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * This service does NOT issue or refresh tokens - that's kb-auth-service's job.
 * It only needs to (a) verify the signature/expiry of a JWT issued elsewhere and
 * (b) populate the SecurityContext so controllers can read the authenticated
 * user via SecurityContextHolder.
 *
 * kb-common intentionally ships JwtAuthenticationFilter / JwtTokenValidator as
 * plain classes (not @Component) so every consuming service wires them
 * explicitly here, with its own choice of public endpoints and entry point -
 * matching the convention already used in kb-auth-service.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtValidator jwtValidator;

    public SecurityConfig(@Value("${kb.security.jwt.secret}") String secret) {
        this.jwtValidator = new JwtValidator(secret);
    }

    @Bean
    public JwtAuthFilter jwtAuthenticationFilter() {
        return new JwtAuthFilter(jwtValidator);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthenticationFilter) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}