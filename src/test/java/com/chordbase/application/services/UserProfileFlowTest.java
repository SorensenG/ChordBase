package com.chordbase.application.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserProfileFlowTest {
    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registerRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "invalid.email",
                                  "email": "invalid-email",
                                  "password": "secret123",
                                  "role": "ROLE_USER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email must be valid"));
    }

    @Test
    void registerNormalizesEmailAndReturnsDescriptionInCurrentUser() throws Exception {
        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "music.user",
                                  "email": "Music.User@Example.COM",
                                  "password": "secret123",
                                  "role": "ROLE_USER",
                                  "description": "Guitarrista e cantor."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("music.user@example.com"))
                .andExpect(jsonPath("$.description").value("Guitarrista e cantor."));

        String loginJson = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "music.user@example.com",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = objectMapper.readTree(loginJson).get("accessToken").asText();

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("music.user@example.com"))
                .andExpect(jsonPath("$.description").value("Guitarrista e cantor."));
    }

    @Test
    void updateProfileChangesDescription() throws Exception {
        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userName": "profile.user",
                                  "email": "profile@example.com",
                                  "password": "secret123",
                                  "role": "ROLE_USER"
                                }
                                """))
                .andExpect(status().isCreated());

        String loginJson = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "profile@example.com",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = objectMapper.readTree(loginJson).get("accessToken").asText();

        mockMvc.perform(put("/users/me/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileImageUrl": null,
                                  "description": "Baixista, compositor e fã de MPB."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Baixista, compositor e fã de MPB."));

        String meJson = mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals("Baixista, compositor e fã de MPB.", objectMapper.readTree(meJson).get("description").asText());
    }
}
