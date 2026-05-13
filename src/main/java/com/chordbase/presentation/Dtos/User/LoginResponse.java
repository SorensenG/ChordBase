package com.chordbase.presentation.Dtos.User;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.chordbase.domain.entities.Role;

import java.util.List;
import java.util.UUID;

public record LoginResponse(String userName, UUID uuid, List<Role> roles, String accessToken, @JsonIgnore String refreshToken) {
}
