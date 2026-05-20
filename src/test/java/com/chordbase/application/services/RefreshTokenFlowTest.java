package com.chordbase.application.services;

import com.chordbase.domain.entities.Role;
import com.chordbase.domain.entities.User;
import com.chordbase.domain.repository.RefreshTokenRepository;
import com.chordbase.domain.repository.UserRepository;
import com.chordbase.domain.valueobjects.UserName;
import com.chordbase.domain.valueobjects.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenFlowTest {
    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void rotateRevokesOldTokenAndCreatesNewActiveToken() {
        User user = userRepository.save(user("refresh-rotation@example.com"));
        String oldRefreshToken = refreshTokenService.createRefreshToken(user);

        var rotation = refreshTokenService.rotate(oldRefreshToken);

        assertNotEquals(oldRefreshToken, rotation.refreshToken());
        assertEquals(user.getUuid(), rotation.user().getUuid());

        var savedTokens = refreshTokenRepository.findAll();
        assertEquals(2, savedTokens.size());
        assertEquals(1, savedTokens.stream().filter(token -> token.isActive(Instant.now())).count());
        assertEquals(1, savedTokens.stream().filter(token -> token.getRevokedAt() != null).count());
        assertThrows(BadCredentialsException.class, () -> refreshTokenService.rotate(oldRefreshToken));
    }

    @Test
    void refreshEndpointRejectsRevokedRefreshToken() throws Exception {
        User user = userRepository.save(user("revoked-refresh@example.com"));
        String refreshToken = refreshTokenService.createRefreshToken(user);
        refreshTokenService.revoke(refreshToken);

        mockMvc.perform(post("/users/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chordDetailRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/chord/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private User user(String email) {
        return User.builder()
                .userName(UserName.of(email.substring(0, email.indexOf('@')).replace('-', '_')))
                .email(email)
                .passwordHash("password-hash")
                .active(true)
                .roles(List.of(Role.builder().role(UserRole.ROLE_USER).build()))
                .build();
    }
}
