package com.moongcheap_backend.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class CorsConfig {

    @Value("${moongcheap.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        Set<String> origins = new LinkedHashSet<>();
        for (String raw : allowedOrigins.split(",")) {
            String origin = raw.trim();
            if (origin.isEmpty()) {
                continue;
            }
            addWithApiSubdomain(origins, origin);
            // https 오리진이면 http 버전도 함께 허용
            if (origin.startsWith("https://")) {
                addWithApiSubdomain(origins, "http://" + origin.substring("https://".length()));
            }
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.copyOf(origins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static void addWithApiSubdomain(Set<String> origins, String origin) {
        origins.add(origin);
        origins.add(origin.replaceFirst("^(https?://)(?!api\\.)", "$1api."));
    }
}
