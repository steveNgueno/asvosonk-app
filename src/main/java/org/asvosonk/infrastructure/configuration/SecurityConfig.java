package org.asvosonk.infrastructure.configuration;

import lombok.RequiredArgsConstructor;
import org.asvosonk.security.application.service.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    // ── Authentication ──────────────────────────────────────
    // Aucun AuthenticationProvider n'est déclaré ici : Spring Security construit
    // lui-même un DaoAuthenticationProvider à partir des beans UserDetailsService
    // et PasswordEncoder. Déclarer les deux faisait cohabiter deux configurations
    // concurrentes (avertissement au démarrage : « UserDetailsService beans will
    // not be used »), pour un comportement identique.

    // ── HTTP security chain ─────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // ── Authorize requests ──────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Public: login page, static assets. /vendor/** holds the locally
                // bundled Bootstrap + icon font: without it the (anonymous) login
                // page would be redirected away from its own stylesheet.
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**",
                                 "/vendor/**", "/favicon.ico", "/favicon.svg").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // ── Response hardening headers ──────────────────
            .headers(headers -> headers
                // Don't leak the internal path of a page to third parties.
                .referrerPolicy(referrer -> referrer.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                        .ReferrerPolicy.SAME_ORIGIN))
                // Everything the UI needs is served by this app (Bootstrap is
                // bundled locally), so lock loading down to the same origin.
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; "
                    + "img-src 'self' data:; "
                    + "font-src 'self'; "
                    + "style-src 'self' 'unsafe-inline'; "
                    + "script-src 'self' 'unsafe-inline'; "
                    + "form-action 'self'; "
                    + "frame-ancestors 'none'; "
                    + "base-uri 'self'"))
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
            );
            // ── CSRF ─────────────────────────────────────────
            // F-46 — CSRF stays enabled by default (Spring Security default,
            // Thymeleaf auto-injects _csrf token) for every endpoint. There is
            // no /api/** controller in this app; a blanket exclusion for it
            // was a dormant hole with no matching functionality to justify it.

        return http.build();
    }

    /**
     * Required for {@code maximumSessions(1)} to work correctly: without it the
     * session registry never learns that a session was destroyed (logout,
     * timeout, browser closed), so stale entries accumulate and concurrency
     * control reasons about sessions that no longer exist.
     */
    @Bean
    public org.springframework.security.web.session.HttpSessionEventPublisher httpSessionEventPublisher() {
        return new org.springframework.security.web.session.HttpSessionEventPublisher();
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
