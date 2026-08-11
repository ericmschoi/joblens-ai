package com.joblens.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is restricted to explicitly configured origins. Credentials are not allowed because the
 * API is stateless and never issues or reads cookies.
 */
@Configuration
class WebConfig implements WebMvcConfigurer {

    private final JoblensProperties properties;

    WebConfig(JoblensProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "Accept")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
