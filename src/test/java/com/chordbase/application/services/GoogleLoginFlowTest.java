package com.chordbase.application.services;

import com.chordbase.application.services.GoogleIdTokenVerifier.GoogleAccount;
import com.chordbase.domain.entities.Role;
import com.chordbase.domain.entities.User;
import com.chordbase.domain.repository.UserRepository;
import com.chordbase.domain.valueobjects.EmailAddress;
import com.chordbase.domain.valueobjects.UserName;
import com.chordbase.domain.valueobjects.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GoogleLoginFlowTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FakeGoogleIdTokenVerifier googleIdTokenVerifier;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        googleIdTokenVerifier.reset();
    }

    @Test
    void googleLoginCreatesUserAndReturnsChordBaseSession() throws Exception {
        String uniqueNamePart = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String googleName = "Novo " + uniqueNamePart;
        String expectedUserName = "novo." + uniqueNamePart;
        googleIdTokenVerifier.account = new GoogleAccount(
                "New.Google." + uniqueNamePart + "@Example.COM",
                true,
                googleName,
                "https://lh3.googleusercontent.com/avatar"
        );

        String loginJson = mockMvc.perform(post("/users/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "valid-google-id-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(jsonPath("$.userName").value(expectedUserName))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var savedUser = userRepository.findByEmail("new.google." + uniqueNamePart + "@example.com").orElseThrow();
        assertThat(savedUser.getProfileImageUrl()).isEqualTo("https://lh3.googleusercontent.com/avatar");
        assertThat(savedUser.getPasswordHash()).isNotBlank();

        String accessToken = objectMapper.readTree(loginJson).get("accessToken").asText();
        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new.google." + uniqueNamePart + "@example.com"))
                .andExpect(jsonPath("$.userName").value(expectedUserName));
    }

    @Test
    void googleLoginReusesExistingUserWithSameVerifiedEmail() throws Exception {
        User existingUser = saveUser("existing.google", "existing.google@example.com", true);
        googleIdTokenVerifier.account = new GoogleAccount(
                "existing.google@example.com",
                true,
                "Different Google Name",
                "https://lh3.googleusercontent.com/new-avatar"
        );

        mockMvc.perform(post("/users/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "valid-google-id-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(existingUser.getUuid().toString()))
                .andExpect(jsonPath("$.userName").value("existing.google"));
    }

    @Test
    void googleLoginRejectsUnverifiedEmail() throws Exception {
        googleIdTokenVerifier.account = new GoogleAccount(
                "unverified@example.com",
                false,
                "Unverified User",
                null
        );

        mockMvc.perform(post("/users/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "valid-google-id-token"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleLoginRejectsInactiveExistingUser() throws Exception {
        saveUser("inactive.google", "inactive.google@example.com", false);
        googleIdTokenVerifier.account = new GoogleAccount(
                "inactive.google@example.com",
                true,
                "Inactive Google",
                null
        );

        mockMvc.perform(post("/users/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "valid-google-id-token"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleLoginAddsSuffixWhenGeneratedUsernameExists() throws Exception {
        saveUser("novo.musico", "novo.musico.owner@example.com", true);
        googleIdTokenVerifier.account = new GoogleAccount(
                "suffix.google@example.com",
                true,
                "Novo Musico",
                null
        );

        mockMvc.perform(post("/users/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "valid-google-id-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("novo.musico.1"));
    }

    @Test
    void googleLoginRejectsInvalidGoogleToken() throws Exception {
        googleIdTokenVerifier.exception = new BadCredentialsException("Google idToken inválido.");

        mockMvc.perform(post("/users/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "invalid-google-id-token"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    private User saveUser(String userName, String email, boolean active) {
        return userRepository.save(User.builder()
                .userName(UserName.of(userName))
                .email(EmailAddress.of(email))
                .passwordHash("hashed-password")
                .roles(List.of(Role.builder().role(UserRole.ROLE_USER).build()))
                .active(active)
                .build());
    }

    @TestConfiguration
    static class GoogleLoginFlowTestConfiguration {
        @Bean
        @Primary
        FakeGoogleIdTokenVerifier fakeGoogleIdTokenVerifier() {
            return new FakeGoogleIdTokenVerifier();
        }
    }

    static class FakeGoogleIdTokenVerifier implements GoogleIdTokenVerifier {
        private GoogleAccount account = new GoogleAccount("user@example.com", true, "User", null);
        private RuntimeException exception;

        @Override
        public GoogleAccount verify(String idToken) {
            if (exception != null) {
                throw exception;
            }
            return account;
        }

        void reset() {
            account = new GoogleAccount("user@example.com", true, "User", null);
            exception = null;
        }
    }
}
