package com.chordbase.presentation.Dtos.User;

import com.chordbase.domain.valueobjects.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterUserDtoRequest(@NotBlank(message = "UserName must not be empty") String userName,
                                     @NotBlank(message = "Email must not be empty ") String email,
                                     @NotBlank(message = "Password must not be empty") String password,
                                     @NotNull(message = "Role must not be empty") UserRole role) {
}
