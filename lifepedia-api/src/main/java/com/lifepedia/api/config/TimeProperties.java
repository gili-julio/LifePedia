package com.lifepedia.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** @param zone fuso que define "hoje" (tarefas); o container roda em UTC */
@ConfigurationProperties("lifepedia.time")
public record TimeProperties(ZoneId zone) {

    @Configuration
    @EnableConfigurationProperties(TimeProperties.class)
    static class Config {

        @Bean
        Clock clock(TimeProperties properties) {
            return Clock.system(properties.zone());
        }
    }
}
