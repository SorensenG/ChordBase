package com.chordbase.presentation.Dtos.User;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(@NotBlank(message = "Google idToken must not be empty") String idToken) {
}
