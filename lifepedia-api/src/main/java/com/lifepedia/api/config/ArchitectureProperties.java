package com.lifepedia.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * @param quota    espaço de cada conta para maquetes e anexos dos projetos dela
 * @param maxModel tamanho máximo de uma maquete (já comprimida no navegador)
 */
@ConfigurationProperties("lifepedia.architecture")
public record ArchitectureProperties(DataSize quota, DataSize maxModel) {

    @Configuration
    @EnableConfigurationProperties(ArchitectureProperties.class)
    static class Config {
    }
}
