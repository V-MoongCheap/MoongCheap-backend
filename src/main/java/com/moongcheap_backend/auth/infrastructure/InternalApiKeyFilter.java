package com.moongcheap_backend.auth.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Internal-Api-Key";
    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final List<String> INTERNAL_PATTERNS = List.of(
        "/api/**/internal",
        "/api/**/internal/**",
        "/api/awarding/**"
    );

    @Value("${moongcheap.security.internal-api-key}")
    private String internalApiKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return INTERNAL_PATTERNS.stream().noneMatch(pattern -> MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        String key = request.getHeader(HEADER_NAME);
        if (internalApiKey.isBlank() || !internalApiKey.equals(key)) {
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
            "{\"success\":false,\"data\":null,\"error\":{\"code\":\"COMMON_401\","
                + "\"message\":\"인증이 필요합니다.\",\"fieldErrors\":[]}}"
        );
    }
}
