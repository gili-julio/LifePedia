package com.lifepedia.api.auth;

import java.time.Instant;

/**
 * Dados do ID token do Google já validado.
 *
 * @param subject  id fixo da conta Google ({@code sub})
 * @param issuedAt quando o Google emitiu o token (confirmação recente de identidade)
 */
public record GoogleIdentity(String subject, String email, boolean emailVerified, String name, Instant issuedAt) {
}
