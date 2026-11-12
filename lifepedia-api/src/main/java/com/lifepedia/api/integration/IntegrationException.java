package com.lifepedia.api.integration;

/** Falha ao falar com um serviço externo; a mensagem é para a pessoa (pt-BR), sem token nem resposta crua. */
public class IntegrationException extends Exception {

    /** {@code AUTH} = token recusado ou revogado (a conta vai para erro e precisa reconectar). */
    public enum Kind { AUTH, FORBIDDEN, NOT_FOUND, RATE_LIMITED, UNAVAILABLE, INVALID }

    private final Kind kind;

    public IntegrationException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static IntegrationException of(Kind kind) {
        return new IntegrationException(kind, switch (kind) {
            case AUTH -> "O serviço recusou o acesso. Conecte de novo com um token válido.";
            case FORBIDDEN -> "O token não tem permissão para isso.";
            case NOT_FOUND -> "Não encontrado no serviço.";
            case RATE_LIMITED -> "O serviço pediu para esperar um pouco. Tente de novo em alguns minutos.";
            case UNAVAILABLE -> "O serviço não respondeu. Tente de novo mais tarde.";
            case INVALID -> "O serviço respondeu algo inesperado.";
        });
    }
}
