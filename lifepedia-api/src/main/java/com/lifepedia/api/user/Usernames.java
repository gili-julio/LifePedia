package com.lifepedia.api.user;

import com.lifepedia.api.shared.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.regex.Pattern;

/** Regras do @username: 3 a 30 caracteres, letras minúsculas, números, ponto e _ (não nas pontas). */
public final class Usernames {

    private static final Pattern VALID = Pattern.compile("^[a-z0-9][a-z0-9._]{1,28}[a-z0-9]$");

    private Usernames() {
    }

    /** Sem @ inicial, sem espaços nas pontas e minúsculo. */
    public static String normalize(String username) {
        String value = username == null ? "" : username.trim();
        if (value.startsWith("@")) value = value.substring(1);
        return value.toLowerCase(Locale.ROOT);
    }

    /** Normaliza e valida; 400 se inválido. */
    public static String require(String username) {
        String value = normalize(username);
        if (!VALID.matcher(value).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Nome de usuário inválido: use de 3 a 30 letras minúsculas, números, ponto ou _ (sem ponto ou _ nas pontas).");
        }
        return value;
    }
}
