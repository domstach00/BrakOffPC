package org.wodrol.brakoffpc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, MobileBearerTokenFilter mobileBearerTokenFilter) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/health",
                                "/favicon.ico",
                                "/favicon-16x16.png",
                                "/favicon-32x32.png",
                                "/apple-touch-icon.png",
                                "/android-chrome-192x192.png",
                                "/android-chrome-512x512.png",
                                "/manifest.json",
                                "/styles.css",
                                "/BrakOff-app-qr.png",
                                "/assets/mobile-config-qr"
                        ).permitAll()
                        .requestMatchers("/api/dashboard", "/api/deliveries/monitors").hasAnyRole("OPERATOR", "MOBILE")
                        .requestMatchers("/api/**").hasRole("MOBILE")
                        .anyRequest().hasRole("OPERATOR")
                )
                .formLogin(Customizer.withDefaults())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getRequestURI().startsWith("/api/")
                        )
                )
                .addFilterBefore(mobileBearerTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(
            @Value("${app.security.operator.username}") String username,
            @Value("${app.security.operator.password}") String password,
            PasswordEncoder passwordEncoder
    ) {
        UserDetails operator = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .roles("OPERATOR")
                .build();
        return new InMemoryUserDetailsManager(operator);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
