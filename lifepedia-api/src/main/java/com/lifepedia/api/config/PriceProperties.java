package com.lifepedia.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Acompanhamento de preço.
 *
 * @param dailyChecks consultas por dia no total (job e "atualizar agora"); passou, o job para até o dia seguinte
 * @param maxWatches  itens acompanhados por pessoa (pessoais e criados nos grupos)
 */
@ConfigurationProperties("lifepedia.prices")
public record PriceProperties(int dailyChecks, int maxWatches) {

    @Configuration
    @EnableConfigurationProperties(PriceProperties.class)
    static class Config {
    }
}
