package com.moongcheap_backend.auth.infrastructure;

import com.moongcheap_backend.auth.infrastructure.session.AuthSessionManager;
import com.moongcheap_backend.common.security.SessionPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object attr = session.getAttribute(AuthSessionManager.PRINCIPAL_ATTR);
                if (attr instanceof SessionPrincipal principal) {
                    List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                            .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                            .toList();
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
