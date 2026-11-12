package com.lifepedia.api.dev;

/** Prompt do resumo do daily (só na API; os dados entram como JSON no fim). */
final class DevDailyPrompt {

    private DevDailyPrompt() {
    }

    static String build(String factsJson) {
        return """
                Você ajuda uma pessoa desenvolvedora a preparar a fala da reunião diária (daily).
                Use só os dados abaixo (JSON). Não invente tarefas, números nem nomes que não estejam neles.
                Escreva em português do Brasil, frases curtas na primeira pessoa, sem emojis e sem travessão.

                Responda só com um JSON assim:
                {"ontem": ["..."], "hoje": ["..."], "bloqueios": ["..."]}

                Regras:
                - "ontem": o que foi feito no dia anterior (commits, MRs abertos ou aceitos, revisões). Agrupe por assunto.
                - "hoje": o que faz sentido seguir hoje (MRs abertos, revisões pedidas, issues, reuniões de hoje).
                - "bloqueios": pipelines quebradas ou MRs parados esperando outra pessoa. Lista vazia se não houver.
                - No máximo 6 itens por lista. Use o título do MR ou da issue para dizer do que se trata.
                - Se não houver atividade no dia anterior, diga isso em "ontem" numa frase só.

                Dados:
                """ + factsJson;
    }
}
