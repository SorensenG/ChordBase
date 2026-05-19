package com.chordbase.presentation.Dtos.User;

import com.chordbase.domain.valueobjects.UserRole;

import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID uuid,
        String userName,
        String email,
        String profileImageUrl,
        List<UserRole> roles
) {
}
