package com.chordbase.presentation.Dtos.User;

import com.chordbase.domain.valueobjects.UserRole;

import java.util.List;
import java.util.UUID;

public record RegisterUserResponse(UUID uuid, String email, String userName, String profileImageUrl, List<UserRole> roles) {

}
