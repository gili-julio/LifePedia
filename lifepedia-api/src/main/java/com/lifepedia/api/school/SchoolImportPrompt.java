package com.lifepedia.api.school;

/** Prompt da leitura do histórico escolar pela IA (montado só aqui, nunca vem do cliente). */
final class SchoolImportPrompt {

    static final String TEXT = """
            Você lê históricos escolares e boletins de qualquer instituição de ensino brasileira (PDF ou foto) e devolve só JSON.

            Formato da resposta (sem texto fora do JSON):
            {
              "curso": "nome do curso ou null",
              "instituicao": "nome da instituição ou null",
              "cargaExigida": {"obrigatorias": número ou null, "optativas": número ou null, "complementares": número ou null},
              "materias": [
                {
                  "periodo": "ano.semestre como 2023.1, ou null se não tiver (matéria aproveitada)",
                  "codigo": "código da disciplina ou null",
                  "nome": "nome da disciplina",
                  "ch": carga horária em horas (número) ou null,
                  "turma": "turma ou null",
                  "tipo": "OBRIGATORIA" | "OPTATIVA" | "ELETIVA" | "COMPLEMENTAR",
                  "situacao": "APR" | "APRN" | "REC" | "REP" | "REPF" | "REPMF" | "REPN" | "REPNF" | "CANC" | "DISP" | "TRANC"
                              | "TRANS" | "INCORP" | "CUMP" | "MATR",
                  "frequencia": porcentagem de 0 a 100 ou null,
                  "media": média final de 0 a 10 ou null,
                  "conceito": "A", "B", "C", "D" ou "E" quando a nota for conceito, senão null,
                  "professor": "nome do professor ou null"
                }
              ]
            }

            Regras:
            - Uma entrada por disciplina cursada, cursando ou aproveitada, na ordem do documento. Ignore disciplinas só pendentes.
            - Situação: aprovado = APR, reprovado por nota = REP, por falta = REPF, cursando ou matriculado = MATR, dispensado
              ou aproveitado = DISP, trancado = TRANC, cancelado = CANC, cumpriu em curso anterior = CUMP.
            - Nota com vírgula vira número com ponto (8,5 -> 8.5). Nota em escala 0 a 100 vira 0 a 10.
            - Não invente valores: o que não estiver no documento fica null.
            - Ignore dados pessoais (nome, documentos, datas de nascimento): não os coloque na resposta.
            """;

    private SchoolImportPrompt() {
    }
}
