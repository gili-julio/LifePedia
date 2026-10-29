package com.lifepedia.api.auth;

import com.lifepedia.api.config.GoogleProperties;
import com.lifepedia.api.shared.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Valida o ID token do botão do Google (assinatura pelas chaves públicas do Google, emissor, audiência e validade). */
@Component
public class GoogleTokenVerifier {

    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final GoogleProperties properties;
    private volatile JwtDecoder decoder;

    public GoogleTokenVerifier(GoogleProperties properties) {
        this.properties = properties;
    }

    public GoogleIdentity verify(String credential) {
        if (!properties.enabled()) throw new BusinessException(HttpStatus.NOT_FOUND, "Login com Google desativado.");
        Jwt jwt;
        try {
            jwt = decoder().decode(credential);
        } catch (JwtException e) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Não foi possível confirmar sua conta Google. Tente de novo.");
        }
        String email = jwt.getClaimAsString("email");
        if (email == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "A conta Google não informou o e-mail.");
        return new GoogleIdentity(jwt.getSubject(), email, Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                jwt.getClaimAsString("name"), jwt.getIssuedAt());
    }

    private JwtDecoder decoder() {
        if (decoder == null) {
            synchronized (this) {
                if (decoder == null) {
                    NimbusJwtDecoder built = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI)
                            .jwsAlgorithm(SignatureAlgorithm.RS256).build();
                    OAuth2TokenValidator<Jwt> issuer = jwt -> ISSUERS.contains(jwt.getClaimAsString("iss"))
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Emissor inválido.", null));
                    OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null
                            && jwt.getAudience().contains(properties.clientId())
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audiência inválida.", null));
                    built.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), issuer, audience));
                    decoder = built;
                }
            }
        }
        return decoder;
    }
}
