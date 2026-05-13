package com.chordbase.infra.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RefreshTokenCookieService {
    private final JwtTokenService jwtTokenService;
    private final String cookieName;
    private final boolean secure;
    private final String sameSite;
    private final String path;

    public RefreshTokenCookieService(
            JwtTokenService jwtTokenService,
            @Value("${security.refresh-cookie.name:refreshToken}") String cookieName,
            @Value("${security.refresh-cookie.secure:false}") boolean secure,
            @Value("${security.refresh-cookie.same-site:Lax}") String sameSite,
            @Value("${security.refresh-cookie.path:/users}") String path) {
        this.jwtTokenService = jwtTokenService;
        this.cookieName = cookieName;
        this.secure = secure;
        this.sameSite = sameSite;
        this.path = path;
    }

    public String getCookieName() {
        return cookieName;
    }

    public ResponseCookie createCookie(String refreshToken) {
        return baseCookie(refreshToken)
                .maxAge(Duration.ofSeconds(jwtTokenService.getRefreshExpirationSeconds()))
                .build();
    }

    public ResponseCookie clearCookie() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path);
    }
}
