package com.chordbase.application.services;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.chordbase.domain.entities.RefreshToken;
import com.chordbase.domain.entities.User;
import com.chordbase.domain.repository.RefreshTokenRepository;
import com.chordbase.infra.security.JwtTokenService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenService jwtTokenService;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtTokenService jwtTokenService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public String createRefreshToken(User user) {
        UUID tokenId = UUID.randomUUID();
        String refreshToken = jwtTokenService.generateRefreshToken(user, tokenId);

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(refreshToken))
                .expiresAt(jwtTokenService.getRefreshExpirationInstant())
                .build();

        refreshTokenRepository.save(token);

        return refreshToken;
    }

    @Transactional
    public RefreshTokenRotation rotate(String rawRefreshToken) {
        RefreshToken currentToken = validate(rawRefreshToken);
        currentToken.setRevokedAt(Instant.now());

        String newRefreshToken = createRefreshToken(currentToken.getUser());

        return new RefreshTokenRotation(currentToken.getUser(), newRefreshToken);
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        try {
            jwtTokenService.verifyRefreshToken(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                    .ifPresent(token -> token.setRevokedAt(Instant.now()));
        } catch (RuntimeException ignored) {
            // Logout should be idempotent even when the cookie has already expired.
        }
    }

    private RefreshToken validate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadCredentialsException("Refresh token ausente.");
        }

        DecodedJWT decodedJWT = jwtTokenService.verifyRefreshToken(rawRefreshToken);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido."));

        if (!refreshToken.isActive(Instant.now())) {
            throw new BadCredentialsException("Refresh token expirado ou revogado.");
        }

        if (!refreshToken.getUser().getEmail().equals(decodedJWT.getSubject())) {
            throw new BadCredentialsException("Refresh token inválido.");
        }

        return refreshToken;
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] tokenHash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(tokenHash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível.", exception);
        }
    }

    public record RefreshTokenRotation(User user, String refreshToken) {
    }
}
