package com.lifepedia.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/** @param dir pasta dos arquivos enviados (fotos de perfil, imagens de grupo e de itens); no Docker, um volume */
@ConfigurationProperties("lifepedia.storage")
public record StorageProperties(Path dir) {

    @Configuration
    @EnableConfigurationProperties(StorageProperties.class)
    static class Config {
    }
}
