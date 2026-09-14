package com.moongcheap_backend.auth.infrastructure;

import com.moongcheap_backend.auth.infrastructure.oauth.CustomOAuth2UserService;
import com.moongcheap_backend.auth.infrastructure.oauth.OAuth2LoginFailureHandler;
import com.moongcheap_backend.auth.infrastructure.oauth.OAuth2LoginSuccessHandler;
import com.moongcheap_backend.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService oauth2UserService;
    private final OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oauth2LoginFailureHandler;
    private final SessionAuthenticationFilter sessionAuthenticationFilter;
    private final IncompleteSignupFilter incompleteSignupFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .addFilterBefore(sessionAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(incompleteSignupFilter, SessionAuthenticationFilter.class)
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET,
                    "/api/group-buys",
                    "/api/group-buys/**"
                ).permitAll()
                .requestMatchers(
                    "/api/auth/signup",
                    "/api/auth/login",
                    "/api/auth/login-id-availability",
                    "/api/members/nicknames/**",
                    "/api/sellers/*/public",
                    "/oauth2/**",
                    "/login/**",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml",
                    "/swagger-resources/**",
                    "/api/products-search/internal/**",
                    "/api/products-search/internal"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> writeError(res,
                    HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED))
                .accessDeniedHandler((req, res, ex) -> writeError(res,
                    HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN))
            )
            .oauth2Login(o -> o
                .userInfoEndpoint(u -> u.userService(oauth2UserService))
                .successHandler(oauth2LoginSuccessHandler)
                .failureHandler(oauth2LoginFailureHandler)
            )
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable())
            .build();
    }

    private void writeError(HttpServletResponse res, int status, ErrorCode code)
        throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        String body = "{\"success\":false,\"data\":null,\"error\":{\"code\":\"" + code.getCode()
            + "\",\"message\":\"" + code.getMessage() + "\",\"fieldErrors\":[]}}";
        res.getWriter().write(body);
    }
}
