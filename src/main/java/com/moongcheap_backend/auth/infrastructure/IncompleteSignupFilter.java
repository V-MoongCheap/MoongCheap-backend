package com.moongcheap_backend.auth.infrastructure;

import com.moongcheap_backend.common.exception.ErrorCode;
import com.moongcheap_backend.common.security.SessionPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class IncompleteSignupFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/auth/social-signup/complete",
            "/api/auth/logout",
            "/api/members/nicknames/availability"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SessionPrincipal principal) {
            if (!principal.termsAgreed() && !ALLOWED_PATHS.contains(request.getRequestURI())) {
                writeError(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response) throws IOException {
        ErrorCode code = ErrorCode.SOCIAL_SIGNUP_INCOMPLETE;
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"data\":null,\"error\":{\"code\":\"" + code.getCode()
                + "\",\"message\":\"" + code.getMessage() + "\",\"fieldErrors\":[]}}"
        );
    }
}
