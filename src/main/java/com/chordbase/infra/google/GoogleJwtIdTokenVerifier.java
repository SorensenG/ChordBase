package com.chordbase.infra.google;

import com.chordbase.application.services.GoogleIdTokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class GoogleJwtIdTokenVerifier implements GoogleIdTokenVerifier {
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String LEGACY_GOOGLE_ISSUER = "accounts.google.com";

    private final String clientId;
    private final JwtDecoder jwtDecoder;

    public GoogleJwtIdTokenVerifier(
            @Value("${google.auth.client-id:}") String clientId,
            @Value("${google.auth.jwk-set-uri:https://www.googleapis.com/oauth2/v3/certs}") String jwkSetUri
    ) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @Override
    public GoogleAccount verify(String idToken) {
        if (clientId.isBlank()) {
            throw new IllegalStateException("google.auth.client-id must be configured");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new BadCredentialsException("Google idToken ausente.");
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (JwtException exception) {
            throw new BadCredentialsException("Google idToken inválido.", exception);
        }

        validateIssuer(jwt);
        validateAudience(jwt);
        validateExpiration(jwt);

        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
        String name = jwt.getClaimAsString("name");
        String pictureUrl = jwt.getClaimAsString("picture");

        if (subject == null || subject.isBlank()) {
            throw new BadCredentialsException("Google idToken sem subject.");
        }
        if (email == null || email.isBlank()) {
            throw new BadCredentialsException("Google idToken sem email.");
        }

        return new GoogleAccount(subject, email, Boolean.TRUE.equals(emailVerified), name, pictureUrl);
    }

    private void validateIssuer(Jwt jwt) {
        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        if (!GOOGLE_ISSUER.equals(issuer) && !LEGACY_GOOGLE_ISSUER.equals(issuer)) {
            throw new BadCredentialsException("Google idToken com emissor inválido.");
        }
    }

    private void validateAudience(Jwt jwt) {
        if (!jwt.getAudience().contains(clientId)) {
            throw new BadCredentialsException("Google idToken com audiência inválida.");
        }
    }

    private void validateExpiration(Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new BadCredentialsException("Google idToken expirado.");
        }
    }
}
