package com.lifepedia.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga as tarefas agendadas (aviso de atrasadas, lembretes); desligadas nos testes, que chamam os métodos direto. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "lifepedia.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
