package com.chordbase.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.chordbase.domain.entities.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

@Service
public class JwtTokenService {
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final String secretKey;
    private final String issuer;
    private final long accessExpirationMinutes;
    private final long refreshExpirationDays;
    private final ZoneId zoneId;

    public JwtTokenService(
            @Value("${security.jwt.secret:}") String secretKey,
            @Value("${security.jwt.issuer:chordbase-api}") String issuer,
            @Value("${security.jwt.access-expiration-minutes:15}") long accessExpirationMinutes,
            @Value("${security.jwt.refresh-expiration-days:7}") long refreshExpirationDays,
            @Value("${security.jwt.time-zone:America/Sao_Paulo}") String timeZone) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("security.jwt.secret must be configured");
        }
        if (accessExpirationMinutes <= 0) {
            throw new IllegalStateException("security.jwt.access-expiration-minutes must be greater than zero");
        }
        if (refreshExpirationDays <= 0) {
            throw new IllegalStateException("security.jwt.refresh-expiration-days must be greater than zero");
        }

        this.secretKey = secretKey;
        this.issuer = issuer;
        this.accessExpirationMinutes = accessExpirationMinutes;
        this.refreshExpirationDays = refreshExpirationDays;
        this.zoneId = ZoneId.of(timeZone);
    }

    public String generateAccessToken(UserDetailsImp user){
        try {
            Algorithm algorithm = Algorithm.HMAC256(secretKey);
            return JWT.create()
                    .withIssuer(issuer)
                    .withIssuedAt(creationDate())
                    .withExpiresAt(accessExpirationDate())
                    .withSubject(user.getEmail())
                    .withClaim("type", ACCESS_TOKEN_TYPE)
                    .sign(algorithm);
        } catch (JWTCreationException exception){
            throw new JWTCreationException("Erro ao gerar access token.", exception);
        }
    }

    public String generateRefreshToken(User user, UUID tokenId){
        try {
            Algorithm algorithm = Algorithm.HMAC256(secretKey);
            return JWT.create()
                    .withIssuer(issuer)
                    .withIssuedAt(creationDate())
                    .withExpiresAt(refreshExpirationDate())
                    .withSubject(user.getEmail())
                    .withJWTId(tokenId.toString())
                    .withClaim("type", REFRESH_TOKEN_TYPE)
                    .sign(algorithm);
        } catch (JWTCreationException exception){
            throw new JWTCreationException("Erro ao gerar refresh token.", exception);
        }
    }

    public String getSubjectFromAccessToken(String token) {
        return verifyToken(token, ACCESS_TOKEN_TYPE).getSubject();
    }

    public DecodedJWT verifyRefreshToken(String token) {
        return verifyToken(token, REFRESH_TOKEN_TYPE);
    }

    public Instant getRefreshExpirationInstant() {
        return refreshExpirationDate();
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpirationDays * 24 * 60 * 60;
    }

    private DecodedJWT verifyToken(String token, String expectedType) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secretKey);
            DecodedJWT decodedJWT = JWT.require(algorithm)
                    .withIssuer(issuer)
                    .build()
                    .verify(token);

            String tokenType = decodedJWT.getClaim("type").asString();
            if (!expectedType.equals(tokenType)) {
                throw new JWTVerificationException("Tipo de token inválido.");
            }

            return decodedJWT;
        } catch (JWTVerificationException exception){
            throw new JWTVerificationException("Token inválido ou expirado.");
        }
    }

    private Instant creationDate() {
        return ZonedDateTime.now(zoneId).toInstant();
    }

    private Instant accessExpirationDate() {
        return ZonedDateTime.now(zoneId).plusMinutes(accessExpirationMinutes).toInstant();
    }

    private Instant refreshExpirationDate() {
        return ZonedDateTime.now(zoneId).plusDays(refreshExpirationDays).toInstant();
    }

}



