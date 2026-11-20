package com.chapelbook.security;

import com.chapelbook.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security. Public: login/register and the health check. Everything else needs a valid
 * token; which role may do what is declared on each controller method with {@code @PreAuthorize}, next
 * to the code it protects.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwt, UserRepository users,
            RestSecurityHandlers handlers) throws Exception {
        http
                // No cookies or sessions are used, so cross-site request forgery does not apply.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .addFilterBefore(new JwtAuthenticationFilter(jwt, users), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
