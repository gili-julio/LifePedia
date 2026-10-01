package com.lifepedia.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("lifepedia.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
