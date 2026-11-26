package com.rehand.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import com.rehand.service.AppUserDetailsService;

/**
 * Web security: form login on /login (the login name is the e-mail address), remember-me,
 * logout via POST /logout, CSRF enabled (Thymeleaf adds the hidden token to every
 * {@code th:action} POST form).
 *
 * Settings:
 * <ul>
 *   <li>{@code app.remember-me-key}: secret used to sign remember-me cookies. The default is only
 *       for development; set a long random value in production.</li>
 *   <li>{@code app.remember-me-days}: how long "keep me logged in" lasts (default 14).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Banned accounts get their own message on the login page; every other failure is "wrong email or password". */
    @Bean
    public AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            boolean banned = exception instanceof DisabledException || exception instanceof LockedException;
            response.sendRedirect(request.getContextPath() + (banned ? "/login?banned" : "/login?error"));
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AppUserDetailsService userDetailsService,
                                                   AuthenticationFailureHandler loginFailureHandler,
                                                   @Value("${app.remember-me-key:rehand-dev-remember-me-key-change-in-production}") String rememberMeKey,
                                                   @Value("${app.remember-me-days:14}") int rememberMeDays) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET,
                        "/",
                        "/listings",
                        "/listings/{id:\\d+}",
                        "/categories/**",
                        "/users/{id:\\d+}",
                        "/api/search/**").permitAll()
                .requestMatchers("/login", "/register", "/error").permitAll()
                .requestMatchers("/css/**", "/js/**", "/img/**", "/uploads/**", "/favicon.ico").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .defaultSuccessUrl("/")
                .failureHandler(loginFailureHandler)
                .permitAll())
            .rememberMe(remember -> remember
                .key(rememberMeKey)
                .rememberMeParameter("remember-me")
                .tokenValiditySeconds(rememberMeDays * 24 * 60 * 60)
                .userDetailsService(userDetailsService))
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll())
            .csrf(Customizer.withDefaults());
        return http.build();
    }
}
