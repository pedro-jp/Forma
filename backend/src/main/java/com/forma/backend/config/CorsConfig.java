package com.forma.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    private final String[] allowedOrigins;

    public CorsConfig(
        @Value("${app.cors.allowed-origins:https://forma-94vp.onrender.com,http://localhost:5173,http://localhost:8080,http://localhost:8085}") String allowedOriginsCsv
    ) {
        this.allowedOrigins = Arrays.stream(allowedOriginsCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toArray(String[]::new);
    }

    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOriginPatterns(allowedOrigins)
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders("Authorization", "Content-Type", "X-Admin-Key")
                    .allowCredentials(true);
            }

            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                registry.addViewController("/").setViewName("forward:/index.html");
                registry.addViewController("/docs").setViewName("forward:/docs.html");
                registry.addViewController("/docs/").setViewName("forward:/docs.html");
                registry.addViewController("/documentacao").setViewName("forward:/docs.html");
                registry.addViewController("/documentacao/").setViewName("forward:/docs.html");
                registry.addRedirectViewController("/admin", "/admin/");
                registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
                registry.addViewController("/privacy").setViewName("forward:/privacy.html");
                registry.addViewController("/privacy/").setViewName("forward:/privacy.html");
                registry.addViewController("/privacidade").setViewName("forward:/privacy.html");
                registry.addViewController("/terms").setViewName("forward:/terms.html");
                registry.addViewController("/terms/").setViewName("forward:/terms.html");
                registry.addViewController("/termos").setViewName("forward:/terms.html");
            }
        };
    }
}
