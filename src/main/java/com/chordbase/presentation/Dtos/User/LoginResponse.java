package com.chordbase.presentation.Dtos.User;

import com.chordbase.domain.valueobjects.UserRole;

import java.util.List;
import java.util.UUID;

public record LoginResponse(String userName, UUID uuid, String profileImageUrl, String description, Boolean active, List<UserRole> roles, String accessToken, String refreshToken) {
}
