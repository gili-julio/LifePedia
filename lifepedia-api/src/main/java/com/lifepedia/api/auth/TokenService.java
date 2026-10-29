package com.lifepedia.api.auth;

import com.lifepedia.api.config.AuthProperties;
import com.lifepedia.api.config.JwtProperties;
import com.lifepedia.api.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Cria a sessão no banco e o JWT que aponta para ela ({@code sid}); sem "lembrar de mim" vale {@code jwt.expiration}. */
@Service
public class TokenService {

    public static final String SESSION_CLAIM = "sid";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtProperties properties;
    private final AuthProperties authProperties;
    private final UserSessionRepository sessions;

    public TokenService(JwtEncoder encoder, JwtDecoder decoder, JwtProperties properties, AuthProperties authProperties,
                        UserSessionRepository sessions) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.properties = properties;
        this.authProperties = authProperties;
        this.sessions = sessions;
    }

    @Transactional
    public IssuedToken issue(User user, boolean remember) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(remember ? authProperties.rememberFor() : properties.expiration());
        UserSession session = sessions.save(new UserSession(user.getId(), remember, expiresAt));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim(SESSION_CLAIM, session.getId().toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt, remember);
    }

    /** Logout: apaga a sessão do token (token inválido ou já vencido é ignorado). */
    @Transactional
    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        try {
            String sid = decoder.decode(token).getClaimAsString(SESSION_CLAIM);
            if (sid != null) sessions.deleteById(UUID.fromString(sid));
        } catch (JwtException | IllegalArgumentException ignored) {
            // já não vale
        }
    }

    /** @param remember cookie persistente até {@code expiresAt}; sem ele o cookie some ao fechar o navegador */
    public record IssuedToken(String value, Instant expiresAt, boolean remember) {
    }
}
