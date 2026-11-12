package com.lifepedia.api.integration;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Quem busca os dados de um tipo de conta externa (agenda, Duolingo, módulo Dev para GitLab/GitHub). */
public interface AccountSyncer {

    boolean supports(IntegrationProvider provider);

    /** Busca no serviço (fora de transação) e grava; devolve quantos itens vieram. */
    int sync(Connection connection) throws IntegrationException;

    /** Nomes do que usa cada conta da pessoa (ex.: projetos do Dev), para a central mostrar. */
    default Map<UUID, List<String>> usage(UUID userId) {
        return Map.of();
    }
}
