package com.chordbase.presentation.Dtos.User;

import com.chordbase.domain.entities.Role;

import java.util.List;
import java.util.UUID;

public record LoginResponse(String userName, UUID uuid, List<Role> roles, String token) {
}
