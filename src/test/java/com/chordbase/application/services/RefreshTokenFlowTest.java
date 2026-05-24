package com.chordbase.application.services;

import com.chordbase.domain.entities.Role;
import com.chordbase.domain.entities.User;
import com.chordbase.domain.repository.RefreshTokenRepository;
import com.chordbase.domain.repository.UserRepository;
import com.chordbase.domain.valueobjects.EmailAddress;
import com.chordbase.domain.valueobjects.UserName;
import com.chordbase.domain.valueobjects.UserRole;
import com.chordbase.infra.security.JwtTokenService;
import com.chordbase.infra.security.UserDetailsImp;
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
import java.util.concurrent.CompletableFuture;

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

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private AdminService adminService;

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
    void refreshEndpointRejectsInactiveUser() throws Exception {
        User user = userRepository.save(user("inactive-refresh@example.com"));
        String refreshToken = refreshTokenService.createRefreshToken(user);
        user.setActive(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/users/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bearerTokenStopsWorkingWhenUserIsDeactivated() throws Exception {
        User user = userRepository.save(user("inactive-access@example.com"));
        String accessToken = jwtTokenService.generateAccessToken(new UserDetailsImp(user));
        user.setActive(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokingUsersTokensMakesExistingRefreshTokensUnusable() {
        User user = userRepository.save(user("revoked-user@example.com"));
        String refreshToken = refreshTokenService.createRefreshToken(user);

        refreshTokenService.revokeAllForUser(user);

        assertThrows(BadCredentialsException.class, () -> refreshTokenService.rotate(refreshToken));
    }

    @Test
    void adminDeactivationRevokesExistingRefreshTokens() {
        User admin = userRepository.save(user("admin@example.com", UserRole.ROLE_ADMIN));
        User target = userRepository.save(user("deactivate@example.com"));
        String refreshToken = refreshTokenService.createRefreshToken(target);

        adminService.updateUserActive(target.getUuid(), false, admin);

        assertThrows(BadCredentialsException.class, () -> refreshTokenService.rotate(refreshToken));
    }

    @Test
    void concurrentRotationProducesOnlyOneSuccessfulSession() {
        User user = userRepository.save(user("concurrent-refresh@example.com"));
        String refreshToken = refreshTokenService.createRefreshToken(user);

        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> tryRotate(refreshToken));
        CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(() -> tryRotate(refreshToken));
        CompletableFuture.allOf(first, second).join();

        assertEquals(1, List.of(first.join(), second.join()).stream().filter(Boolean::booleanValue).count());
    }

    @Test
    void chordDetailRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/chord/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private boolean tryRotate(String refreshToken) {
        try {
            refreshTokenService.rotate(refreshToken);
            return true;
        } catch (BadCredentialsException exception) {
            return false;
        }
    }

    private User user(String email) {
        return user(email, UserRole.ROLE_USER);
    }

    private User user(String email, UserRole role) {
        return User.builder()
                .userName(UserName.of(email.substring(0, email.indexOf('@')).replace('-', '_')))
                .email(EmailAddress.of(email))
                .passwordHash("password-hash")
                .active(true)
                .roles(List.of(Role.builder().role(role).build()))
                .build();
    }
}
