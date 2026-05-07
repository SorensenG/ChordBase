package com.chordbase.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class JwtTokenService {
    private final String secretKey;
    private final String issuer;
    private final long expirationHours;
    private final ZoneId zoneId;

    public JwtTokenService(
            @Value("${security.jwt.secret:}") String secretKey,
            @Value("${security.jwt.issuer:chordbase-api}") String issuer,
            @Value("${security.jwt.expiration-hours:4}") long expirationHours,
            @Value("${security.jwt.time-zone:America/Sao_Paulo}") String timeZone) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("security.jwt.secret must be configured");
        }
        if (expirationHours <= 0) {
            throw new IllegalStateException("security.jwt.expiration-hours must be greater than zero");
        }

        this.secretKey = secretKey;
        this.issuer = issuer;
        this.expirationHours = expirationHours;
        this.zoneId = ZoneId.of(timeZone);
    }

    public String generateToken(UserDetailsImp user){
        try {
            Algorithm algorithm = Algorithm.HMAC256(secretKey);
            return JWT.create()
                    .withIssuer(issuer) // Define o emissor do token
                    .withIssuedAt(creationDate()) // Define a data de emissão do token
                    .withExpiresAt(expirationDate()) // Define a data de expiração do token
                    .withSubject(user.getUsername()) // Define o assunto do token (neste caso, o nome de usuário)
                    .sign(algorithm); // Assina o token usando o algoritmo especificado
        } catch (JWTCreationException exception){
            throw new JWTCreationException("Erro ao gerar token.", exception);
        }
    }


    public String getSubjectFromToken(String token) {
        try {
            // Define o algoritmo HMAC SHA256 para verificar a assinatura do token passando a chave secreta definida
            Algorithm algorithm = Algorithm.HMAC256(secretKey);
            return JWT.require(algorithm)
                    .withIssuer(issuer) // Define o emissor do token
                    .build()
                    .verify(token) // Verifica a validade do token
                    .getSubject(); // Obtém o assunto (neste caso, o nome de usuário) do token
        } catch (JWTVerificationException exception){
            throw new JWTVerificationException("Token inválido ou expirado.");
        }
    }

    private Instant creationDate() {
        return ZonedDateTime.now(zoneId).toInstant();
    }

    private Instant expirationDate() {
        return ZonedDateTime.now(zoneId).plusHours(expirationHours).toInstant();
    }

}




