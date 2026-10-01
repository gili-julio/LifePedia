package com.lifepedia.api.shared;

import org.springframework.http.HttpStatus;

/** Regra de negócio violada, com status HTTP e mensagem para o usuário. */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
