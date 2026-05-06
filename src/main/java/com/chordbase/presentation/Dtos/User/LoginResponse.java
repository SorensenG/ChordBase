package com.chordbase.presentation.Dtos.User;

import com.chordbase.domain.entities.Role;

import java.util.List;

public record LoginResponse(String userName, String password, List<Role> roles, String token) {
}
