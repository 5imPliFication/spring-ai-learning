package com.example.ai.config;

import com.example.ai.entity.User;
import com.example.ai.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    private static final long ACTIVITY_UPDATE_THRESHOLD_MINUTES = 5;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.isValid(token)) {
                String username = jwtUtil.extractUsername(token);
                userRepository.findByUsernameAndDeletedAtIsNull(username).ifPresent(user -> {
                    // Only update lastActiveAt if stale (older than threshold)
                    // This prevents a SELECT+UPDATE on every single request
                    if (shouldUpdateActivity(user)) {
                        user.setLastActiveAt(Instant.now());
                        userRepository.save(user);
                    }

                    var auth = new UsernamePasswordAuthenticationToken(
                            user, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldUpdateActivity(User user) {
        if (user.getLastActiveAt() == null) {
            return true;
        }
        return user.getLastActiveAt()
                .isBefore(Instant.now().minus(ACTIVITY_UPDATE_THRESHOLD_MINUTES, ChronoUnit.MINUTES));
    }
}
