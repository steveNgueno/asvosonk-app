package org.asvosonk.infrastructure.configuration;

import lombok.RequiredArgsConstructor;
import org.asvosonk.security.application.service.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.*;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // enables @PreAuthorize("hasAuthority('PERMISSION_CODE')") on controllers
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    // ── Password encoder ────────────────────────────────────

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // ── Authentication provider ─────────────────────────────

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    // ── HTTP security chain ─────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // ── Authorize requests ──────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Public: login page, static assets
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // ── Form login ──────────────────────────────────
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("login")
                .passwordParameter("password")
                .successHandler(loginSuccessHandler())
                .failureHandler(loginFailureHandler())
                .permitAll()
            )

            // ── Logout ──────────────────────────────────────
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )

            // ── Session management ──────────────────────────
            .sessionManagement(session -> session
                .maximumSessions(1)              // one session per user at a time
                .expiredUrl("/login?expired")
            )

            // ── CSRF ─────────────────────────────────────────
            // Enabled by default (Spring Security default) — Thymeleaf auto-injects _csrf token
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**")); // if we ever add internal API endpoints

        return http.build();
    }

    // ── Success / failure handlers (brute-force tracking) ──

    @Bean
    public AuthenticationSuccessHandler loginSuccessHandler() {
        return (request, response, authentication) -> {
            String login = authentication.getName();
            userDetailsService.handleLoginSuccess(login);
            // Redirect to dashboard
            response.sendRedirect("/dashboard");
        };
    }

    @Bean
    public AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            String login = request.getParameter("login");
            if (login != null && !login.isBlank()) {
                userDetailsService.handleLoginFailure(login);
            }
            response.sendRedirect("/login?error");
        };
    }
}
