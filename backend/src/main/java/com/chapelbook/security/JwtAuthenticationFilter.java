package com.chapelbook.security;

import com.chapelbook.user.User;
import com.chapelbook.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code Authorization: Bearer <token>}, verifies it and, if the token is valid and the user
 * still exists and is active, marks the request as authenticated.
 *
 * <p>The role used for authorisation comes from the database, not from the token, so deactivating a
 * user or changing their role takes effect on the very next request instead of when the token expires.
 * A missing or invalid token simply leaves the request unauthenticated; the security chain then answers 401.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwt;
    private final UserRepository users;

    public JwtAuthenticationFilter(JwtService jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            jwt.parse(header.substring(BEARER.length()).trim())
                    .flatMap(claims -> users.findById(claims.userId()))
                    .filter(User::isActive)
                    .ifPresent(this::authenticate);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(User user) {
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
