package com.chordbase.presentation.Dtos.User;

import java.util.UUID;

public record UserSearchResponse(UUID uuid, String userName, String profileImageUrl) {
}
