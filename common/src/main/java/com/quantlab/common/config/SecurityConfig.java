package com.quantlab.common.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // Disable CSRF if not needed
                .cors(AbstractHttpConfigurer::disable) // Disable CORS completely
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()); // Allow all requests


        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
      //  logger.info("inside corsConfigurationSource, before each call");
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173/","http://localhost:5173",
                "http://localhost:5174/","http://localhost:5174",
                "https://newdsmobileuat.dhanistocks.com","https://newdsmobileuat.dhanistocks.com/",
                "https://indiabulls.quantlabdemo.com/",
                "https://algo-qa.dhanistocks.com/","https://algo.dhanistocks.com","https://*.dhanistocks.com",
                "https://algo-qa.ibullssecurities.com/", "https://algo-qa.ibullssecurities.com",
                "https://algo.ibullssecurities.com/", "https://algo.ibullssecurities.com"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
